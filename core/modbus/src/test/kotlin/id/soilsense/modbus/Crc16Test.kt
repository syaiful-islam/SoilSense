package id.soilsense.modbus

import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Vectors are the example frames from the CWT NPK manual V1.4, pages 4–7. */
class Crc16Test {

    private val manualFrames = listOf(
        "01 03 00 00 00 07 04 08",
        "01 03 0E 01 D0 01 4C 00 2C 00 5A 00 20 00 58 00 68 70 29",
        "01 06 00 04 00 20 C9 D3",
        "01 06 00 05 00 58 98 31",
        "01 06 00 06 00 68 68 25",
        "01 06 07 D0 00 02 08 86",
        "01 06 07 D1 00 02 59 46",
        "FF 03 07 D0 00 01 91 59",
        "FF 03 02 00 01 50 50",
    )

    @Test
    fun `manual frames have valid CRC`() {
        manualFrames.forEach { frame -> assertTrue(Crc16.isValid(hex(frame)), frame) }
    }

    @Test
    fun `appendCrc reproduces manual frames`() {
        manualFrames.forEach { frame ->
            val bytes = hex(frame)
            assertContentEquals(bytes, Crc16.appendCrc(bytes.copyOf(bytes.size - 2)), frame)
        }
    }

    @Test
    fun `corrupted frame is rejected`() {
        val frame = hex("01 03 00 00 00 07 04 08")
        frame[3] = 0x01
        assertFalse(Crc16.isValid(frame))
    }

    @Test
    fun `frames shorter than three bytes are invalid`() {
        assertFalse(Crc16.isValid(hex("FF FF")))
    }
}
