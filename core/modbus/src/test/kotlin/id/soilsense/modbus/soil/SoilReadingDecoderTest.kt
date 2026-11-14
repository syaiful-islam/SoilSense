package id.soilsense.modbus.soil

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SoilReadingDecoderTest {

    @Test
    fun `decodes manual example`() {
        // Registers from the manual's example reply: 01D0 014C 002C 005A 0020 0058 0068.
        val reading = SoilReadingDecoder.decode(intArrayOf(464, 332, 44, 90, 32, 88, 104))

        assertEquals(46.4, reading.moisturePercent, 1e-9)
        assertEquals(33.2, reading.temperatureCelsius, 1e-9)
        assertEquals(44, reading.conductivityMicroSiemensPerCm)
        assertEquals(9.0, reading.ph, 1e-9)
        assertEquals(32, reading.nitrogenMgPerKg)
        assertEquals(88, reading.phosphorusMgPerKg)
        assertEquals(104, reading.potassiumMgPerKg)
        assertNull(reading.salinityMgPerL)
        assertNull(reading.tdsMgPerL)
    }

    @Test
    fun `decodes salinity and TDS when present`() {
        val reading = SoilReadingDecoder.decode(intArrayOf(464, 332, 44, 90, 32, 88, 104, 24, 22))

        assertEquals(24, reading.salinityMgPerL)
        assertEquals(22, reading.tdsMgPerL)
    }

    @Test
    fun `negative temperature is two's complement`() {
        // -5.5 °C = -55 = 0xFFC9
        val reading = SoilReadingDecoder.decode(intArrayOf(0, 0xFFC9, 0, 0, 0, 0, 0))

        assertEquals(-5.5, reading.temperatureCelsius, 1e-9)
    }

    @Test
    fun `too few registers are rejected`() {
        assertFailsWith<IllegalArgumentException> { SoilReadingDecoder.decode(IntArray(6)) }
    }
}
