package id.soilsense.modbus.soil

import id.soilsense.modbus.ModbusRtuClient
import id.soilsense.modbus.SimulatedProbe
import id.soilsense.modbus.hex
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SoilCalibrationTest {

    /** A probe with factory calibration, a written (frozen) N value and every calibration register present. */
    private fun factoryProbe() = SimulatedProbe(
        registers = mutableMapOf(
            0x0004 to 32, 0x0005 to 88, 0x0006 to 104,
            0x0022 to 0, 0x0023 to 55, 0x0024 to 50,
            0x0050 to 0, 0x0051 to 0, 0x0052 to 0, 0x0053 to 0,
            0x04E8 to 0x3F80, 0x04E9 to 0, 0x04EA to 0,
            0x04F2 to 0x3F80, 0x04F3 to 0, 0x04F4 to 0,
            0x04FC to 0x3F80, 0x04FD to 0, 0x04FE to 0,
        ),
    )

    private fun sensorOn(probe: SimulatedProbe) = SoilSensor(ModbusRtuClient(probe))

    @Test
    fun `reads factory calibration`() = runTest {
        val calibration = sensorOn(factoryProbe()).readCalibration(slaveId = 1)

        assertEquals(0, calibration.offsets.getValue(OffsetParameter.PH))
        assertEquals(55, calibration.factors.getValue(FactorParameter.SALINITY))
        assertEquals(50, calibration.factors.getValue(FactorParameter.TDS))
        assertEquals(NpkCalibration.DEFAULT, calibration.npk.getValue(Nutrient.POTASSIUM))
    }

    @Test
    fun `negative offsets are written as two's complement and read back signed`() = runTest {
        val probe = factoryProbe()

        val calibration = sensorOn(probe).apply(1, CalibrationWrite.Offset(OffsetParameter.TEMPERATURE, -15))

        assertEquals(0xFFF1, probe.registers[0x0050])
        assertEquals(-15, calibration.offsets.getValue(OffsetParameter.TEMPERATURE))
    }

    @Test
    fun `npk factor is written big-endian with function 0x10`() = runTest {
        val probe = factoryProbe()

        val calibration = sensorOn(probe).apply(1, CalibrationWrite.Npk(Nutrient.PHOSPHORUS, NpkCalibration(1.25f, -3)))

        // 1.25f = 0x3FA00000
        assertEquals(0x3FA0, probe.registers[0x04F2])
        assertEquals(0x0000, probe.registers[0x04F3])
        assertEquals(0xFFFD, probe.registers[0x04F4])
        assertEquals(NpkCalibration(1.25f, -3), calibration.npk.getValue(Nutrient.PHOSPHORUS))
        assertTrue(probe.requests.any { it[1] == 0x10.toByte() })
    }

    @Test
    fun `reset NPK sends exactly the vendor frames`() = runTest {
        val probe = factoryProbe().apply {
            registers[0x04EA] = 7
            registers[0x04E8] = 0x4000 // factor 2.0
        }

        sensorOn(probe).apply(1, CalibrationWrite.ResetNpk)

        val writes = probe.requests.filter { it[1] != 0x03.toByte() }.map { it.toList() }
        val vendor = listOf(
            "01 06 00 04 FF FF C9 BB",
            "01 06 00 05 FF FF 98 7B",
            "01 06 00 06 FF FF 68 7B",
            "01 10 04 E8 00 02 04 3F 80 00 00 C3 7D",
            "01 06 04 EA 00 00 A9 0E",
            "01 10 04 F2 00 02 04 3F 80 00 00 42 0E",
            "01 06 04 F4 00 00 C9 08",
            "01 10 04 FC 00 02 04 3F 80 00 00 C3 82",
            "01 06 04 FE 00 00 E9 0A",
        ).map { hex(it).toList() }
        assertEquals(vendor, writes)
    }

    @Test
    fun `restore defaults clears offsets and restores factors`() = runTest {
        val probe = factoryProbe().apply {
            registers[0x0053] = 4
            registers[0x0023] = 70
            registers[0x0022] = 20
        }

        val calibration = sensorOn(probe).apply(1, CalibrationWrite.RestoreDefaults)

        assertTrue(calibration.offsets.values.all { it == 0 })
        assertEquals(mapOf(
            FactorParameter.EC_TEMPERATURE_COEFFICIENT to 0,
            FactorParameter.SALINITY to 55,
            FactorParameter.TDS to 50,
        ), calibration.factors)
        assertTrue(calibration.npk.values.all { it == NpkCalibration.DEFAULT })
    }

    @Test
    fun `offset math adds the error to the current offset`() {
        // Probe shows 7.9 pH with offset +2 (0.2 pH); the reference meter says 7.5.
        assertEquals(-2, CalibrationMath.offset(OffsetParameter.PH, currentRaw = 2, reading = 7.9, reference = 7.5))
        // 25.0 °C shown, 23.6 °C real.
        assertEquals(-14, CalibrationMath.offset(OffsetParameter.TEMPERATURE, currentRaw = 0, reading = 25.0, reference = 23.6))
        assertEquals(150, CalibrationMath.offset(OffsetParameter.CONDUCTIVITY, currentRaw = 0, reading = 850.0, reference = 1000.0))
    }

    @Test
    fun `offset math refuses results outside int16`() {
        assertNull(CalibrationMath.offset(OffsetParameter.CONDUCTIVITY, currentRaw = 32_000, reading = 0.0, reference = 1_000.0))
    }

    @Test
    fun `factor math converts engineering values and checks the range`() {
        assertEquals(60, CalibrationMath.factorRaw(FactorParameter.SALINITY, 0.6))
        assertEquals(25, CalibrationMath.factorRaw(FactorParameter.EC_TEMPERATURE_COEFFICIENT, 2.5))
        assertNull(CalibrationMath.factorRaw(FactorParameter.TDS, 1.2))
        assertNull(CalibrationMath.factorRaw(FactorParameter.TDS, -0.1))
    }

    @Test
    fun `npk one point keeps the factor and shifts the offset`() {
        val result = CalibrationMath.npkOnePoint(NpkCalibration(1.5f, 4), reading = 60, reference = 45)

        assertEquals(NpkCalibration(1.5f, -11), result)
    }

    @Test
    fun `npk two point fits a line through both references`() {
        // Factory calibration: readings equal raw values 40 and 140; lab says 50 and 200.
        val result = CalibrationMath.npkTwoPoint(NpkCalibration.DEFAULT, 40, 50, 140, 200)

        assertEquals(NpkCalibration(1.5f, -10), result)
    }

    @Test
    fun `npk two point undoes the current calibration first`() {
        // Same raw values 40 and 140, but seen through factor 2.0 and offset 5 → readings 85 and 285.
        val result = CalibrationMath.npkTwoPoint(NpkCalibration(2.0f, 5), 85, 50, 285, 200)

        assertEquals(NpkCalibration(1.5f, -10), result)
    }

    @Test
    fun `npk two point rejects samples too close together or a falling line`() {
        assertNull(CalibrationMath.npkTwoPoint(NpkCalibration.DEFAULT, 40, 50, 42, 200))
        assertNull(CalibrationMath.npkTwoPoint(NpkCalibration.DEFAULT, 40, 200, 140, 50))
    }

    @Test
    fun `apply returns what the probe stores`() = runTest {
        val probe = factoryProbe()

        val calibration = sensorOn(probe).apply(1, CalibrationWrite.Factor(FactorParameter.TDS, 64))

        assertEquals(64, probe.registers[0x0024])
        assertEquals(64, calibration.factors.getValue(FactorParameter.TDS))
        assertContentEquals(hex("01 06 00 24 00 40 C8 31"), probe.requests.first())
    }
}
