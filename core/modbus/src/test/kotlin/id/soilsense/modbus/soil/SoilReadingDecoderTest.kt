package id.soilsense.modbus.soil

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SoilReadingDecoderTest {

    @Test
    fun `decodes manual example`() {
        // Registers from the manual's example reply: 01D0 014C 002C 005A 0020 0058 0068.
        val reading = SoilReadingDecoder.decode(intArrayOf(464, 332, 44, 90, 32, 88, 104))

        assertEquals(
            SoilReading(
                moisturePercent = 46.4,
                temperatureCelsius = 33.2,
                conductivityMicroSiemensPerCm = 44,
                ph = 9.0,
                nitrogenMgPerKg = 32,
                phosphorusMgPerKg = 88,
                potassiumMgPerKg = 104,
            ),
            reading,
        )
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
