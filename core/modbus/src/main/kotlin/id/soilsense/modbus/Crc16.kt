package id.soilsense.modbus

/**
 * CRC-16/MODBUS (polynomial 0xA001 reflected, initial value 0xFFFF).
 *
 * On the wire the CRC is transmitted low byte first, see [appendCrc].
 */
public object Crc16 {

    private val table: IntArray = IntArray(256) { index ->
        var crc = index
        repeat(8) {
            crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1
        }
        crc
    }

    /** Computes the CRC of [data] in the range [offset] until [offset] + [length]. */
    public fun compute(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
        require(offset >= 0 && length >= 0 && offset + length <= data.size) {
            "Range [$offset, ${offset + length}) is outside of array of size ${data.size}"
        }
        var crc = 0xFFFF
        for (i in offset until offset + length) {
            crc = (crc ushr 8) xor table[(crc xor data[i].toInt()) and 0xFF]
        }
        return crc
    }

    /** Returns a copy of [payload] followed by its CRC (low byte, then high byte). */
    public fun appendCrc(payload: ByteArray): ByteArray {
        val crc = compute(payload)
        return payload + byteArrayOf((crc and 0xFF).toByte(), (crc ushr 8 and 0xFF).toByte())
    }

    /** True when the last two bytes of the first [length] bytes of [frame] are a valid CRC of the bytes before them. */
    public fun isValid(frame: ByteArray, length: Int = frame.size): Boolean {
        if (length < 3 || length > frame.size) return false
        val expected = compute(frame, 0, length - 2)
        val actual = (frame[length - 2].toInt() and 0xFF) or ((frame[length - 1].toInt() and 0xFF) shl 8)
        return expected == actual
    }
}
