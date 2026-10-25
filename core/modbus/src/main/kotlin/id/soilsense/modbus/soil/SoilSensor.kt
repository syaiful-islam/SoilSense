package id.soilsense.modbus.soil

import id.soilsense.modbus.ModbusException
import id.soilsense.modbus.ModbusRtuClient

/**
 * High-level access to the 7-in-1 soil probe.
 *
 * The manual documents salinity/TDS registers, but its own example only reads 7 registers and not
 * every firmware revision exposes the extra two. The first read therefore tries 9 registers and,
 * if the probe rejects that, remembers to read 7 from then on.
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

    /** Reads the probe's configured slave ID through the 0xFF query address (only one probe may be on the bus). */
    public suspend fun queryAddress(): Int =
        client.readHoldingRegisters(
            slaveId = SoilSensorRegisters.BROADCAST_QUERY_ADDRESS,
            startAddress = SoilSensorRegisters.SLAVE_ID,
            quantity = 1,
        ).first()

    /** Forget register-support detection, e.g. after switching to a different probe. */
    public fun reset() {
        extendedRegistersSupported = null
    }

    private suspend fun fallBackToBasic(slaveId: Int): SoilReading {
        val reading = readBasic(slaveId)
        extendedRegistersSupported = false
        return reading
    }

    private suspend fun readBasic(slaveId: Int): SoilReading =
        SoilReadingDecoder.decode(
            client.readHoldingRegisters(slaveId, SoilSensorRegisters.MOISTURE, SoilSensorRegisters.BASIC_COUNT),
        )
}
