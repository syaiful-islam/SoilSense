package id.soilsense.modbus.soil

import id.soilsense.modbus.ModbusException
import id.soilsense.modbus.ModbusRtuClient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * High-level access to the 7-in-1 soil probe.
 *
 * The manual documents salinity/TDS registers (0x0007–0x0008), but its own example only reads 7
 * registers and not every firmware revision exposes the extra two. The first read therefore tries
 * 9 registers and, if the probe rejects that or stays silent, reads 7 from then on.
 */
public class SoilSensor(private val client: ModbusRtuClient) {

    @Volatile
    private var extendedRegistersSupported: Boolean? = null

    public suspend fun read(slaveId: Int): SoilReading {
        if (extendedRegistersSupported == false) return readBasic(slaveId)

        val registers = try {
            client.readHoldingRegisters(slaveId, SoilSensorRegisters.MOISTURE, SoilSensorRegisters.EXTENDED_COUNT)
        } catch (e: ModbusException.ExceptionResponse) {
            if (extendedRegistersSupported == true ||
                e.exceptionCode != ModbusException.ExceptionResponse.ILLEGAL_DATA_ADDRESS
            ) {
                throw e
            }
            return fallBackToBasic(slaveId)
        } catch (e: ModbusException.ResponseTimeout) {
            // Some firmwares stay silent instead of answering with an exception. Only probe once:
            // after support is known, a timeout means a real communication problem.
            if (extendedRegistersSupported == true) throw e
            return fallBackToBasic(slaveId)
        }
        extendedRegistersSupported = true
        return SoilReadingDecoder.decode(registers)
    }

    /**
     * Asks "who is there?" through the 0xFF enquiry address (manual: "Enquiry slave ID") and returns
     * the slave ID the probe reports. The reply may come from 0xFF or from the probe's real address.
     * Only one probe may be on the bus.
     */
    public suspend fun queryAddress(timeout: Duration = ModbusRtuClient.DEFAULT_RESPONSE_TIMEOUT): Int {
        val reply = client.readHoldingRegistersFromAnyAddress(
            requestAddress = SoilSensorRegisters.BROADCAST_QUERY_ADDRESS,
            startAddress = SoilSensorRegisters.SLAVE_ID,
            quantity = 1,
            timeout = timeout,
        )
        val stored = reply.registers.first()
        return when {
            stored in SoilSensorRegisters.SLAVE_ID_RANGE -> stored
            reply.slaveId in SoilSensorRegisters.SLAVE_ID_RANGE -> reply.slaveId
            else -> throw ModbusException.UnexpectedResponse("Enquiry reply reports slave ID $stored")
        }
    }

    /**
     * Whether a probe answers at [slaveId] within [timeout]. An exception reply counts too: something
     * with that address is on the bus. Used to scan addresses when the 0xFF enquiry gets no answer.
     */
    public suspend fun answersAt(slaveId: Int, timeout: Duration = SCAN_TIMEOUT): Boolean =
        try {
            client.readHoldingRegisters(slaveId, SoilSensorRegisters.SLAVE_ID, quantity = 1, timeout = timeout)
            true
        } catch (_: ModbusException.ExceptionResponse) {
            true
        } catch (_: ModbusException) {
            false
        }

    /** Reads the slave ID and baud rate stored in the probe, one register at a time like the vendor tool. */
    public suspend fun readProbeConfig(slaveId: Int): ProbeConfig = ProbeConfig(
        slaveId = readWord(slaveId, SoilSensorRegisters.SLAVE_ID),
        baudRate = ProbeBaudRate.fromCode(readWord(slaveId, SoilSensorRegisters.BAUD_RATE)),
    )

    /**
     * Stores [newSlaveId] in the probe (manual: "Set slave ID"). From then on the probe answers
     * only to the new address, possibly only after a power cycle.
     */
    public suspend fun writeSlaveId(slaveId: Int, newSlaveId: Int) {
        require(newSlaveId in SoilSensorRegisters.SLAVE_ID_RANGE) { "Slave ID $newSlaveId outside ${SoilSensorRegisters.SLAVE_ID_RANGE}" }
        client.writeSingleRegister(slaveId, SoilSensorRegisters.SLAVE_ID, newSlaveId)
    }

    /** Stores [baudRate] in the probe (manual: "Set baud rate"); 2400, 4800 or 9600 only. */
    public suspend fun writeBaudRate(slaveId: Int, baudRate: Int) {
        val code = requireNotNull(ProbeBaudRate.code(baudRate)) { "Unsupported baud rate $baudRate" }
        client.writeSingleRegister(slaveId, SoilSensorRegisters.BAUD_RATE, code)
    }

    /**
     * Reads every calibration register. Registers are read one item at a time, the way the
     * vendor's Config Tool does, rather than in blocks that span gaps in the register map.
     */
    public suspend fun readCalibration(slaveId: Int): CalibrationSettings {
        val offsets = OffsetParameter.entries.associateWith { readSigned(slaveId, it.register) }
        val factors = FactorParameter.entries.associateWith { readWord(slaveId, it.register) }
        val npk = Nutrient.entries.associateWith { nutrient ->
            val factorWords = client.readHoldingRegisters(slaveId, nutrient.factorRegister, 2)
            NpkCalibration(
                factor = Float.fromBits((factorWords[0] shl 16) or factorWords[1]),
                offset = readSigned(slaveId, nutrient.offsetRegister),
            )
        }
        return CalibrationSettings(offsets, factors, npk)
    }

    /** Writes [change] to the probe's memory and returns the calibration read back afterwards. */
    public suspend fun apply(slaveId: Int, change: CalibrationWrite): CalibrationSettings {
        when (change) {
            is CalibrationWrite.Offset ->
                client.writeSingleRegister(slaveId, change.parameter.register, change.raw and 0xFFFF)
            is CalibrationWrite.Factor ->
                client.writeSingleRegister(slaveId, change.parameter.register, change.raw)
            is CalibrationWrite.Npk -> writeNpk(slaveId, change.nutrient, change.calibration)
            CalibrationWrite.ResetNpk -> resetNpk(slaveId)
            CalibrationWrite.RestoreDefaults -> {
                OffsetParameter.entries.forEach { client.writeSingleRegister(slaveId, it.register, 0) }
                FactorParameter.entries.forEach { client.writeSingleRegister(slaveId, it.register, it.defaultRaw) }
                resetNpk(slaveId)
            }
        }
        return readCalibration(slaveId)
    }

    private suspend fun resetNpk(slaveId: Int) {
        Nutrient.entries.forEach { client.writeSingleRegister(slaveId, it.dataRegister, NPK_RESUME_MEASURING) }
        Nutrient.entries.forEach { writeNpk(slaveId, it, NpkCalibration.DEFAULT) }
    }

    private suspend fun writeNpk(slaveId: Int, nutrient: Nutrient, calibration: NpkCalibration) {
        // Big-endian float, high word first ("FloatReverse" in the vendor tool), written with 0x10.
        val bits = calibration.factor.toRawBits()
        client.writeMultipleRegisters(slaveId, nutrient.factorRegister, intArrayOf(bits ushr 16 and 0xFFFF, bits and 0xFFFF))
        client.writeSingleRegister(slaveId, nutrient.offsetRegister, calibration.offset and 0xFFFF)
    }

    private suspend fun readWord(slaveId: Int, register: Int): Int =
        client.readHoldingRegisters(slaveId, register, 1).first()

    private suspend fun readSigned(slaveId: Int, register: Int): Int =
        readWord(slaveId, register).let { if (it >= 0x8000) it - 0x10000 else it }

    /** Forget register-support detection, e.g. after switching to a different probe. */
    public fun reset() {
        extendedRegistersSupported = null
    }

    private suspend fun fallBackToBasic(slaveId: Int): SoilReading {
        val reading = readBasic(slaveId)
        extendedRegistersSupported = false
        return reading
    }

    private companion object {
        /** Written to an N/P/K data register, makes the probe measure again instead of holding a written value. */
        const val NPK_RESUME_MEASURING = 0xFFFF

        /**
         * Per-address wait while scanning. A 7-byte reply takes ~30 ms on the wire at 2400 bps; the
         * rest is margin for the probe's processing time. 254 addresses × 3 baud rates ≈ 3 minutes.
         */
        val SCAN_TIMEOUT: Duration = 250.milliseconds
    }

    private suspend fun readBasic(slaveId: Int): SoilReading =
        SoilReadingDecoder.decode(
            client.readHoldingRegisters(slaveId, SoilSensorRegisters.MOISTURE, SoilSensorRegisters.MEASUREMENT_COUNT),
        )
}
