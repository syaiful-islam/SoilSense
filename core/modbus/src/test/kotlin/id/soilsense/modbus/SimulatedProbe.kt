package id.soilsense.modbus

/**
 * In-memory Modbus slave: answers 0x03 reads and applies 0x06 / 0x10 writes to [registers], with
 * real CRCs, so higher layers can be tested against a probe that actually keeps what is written.
 * Unknown registers answer "illegal data address" like a real device.
 */
class SimulatedProbe(
    private val slaveId: Int = 1,
    val registers: MutableMap<Int, Int> = mutableMapOf(),
) : SerialTransport {

    val requests = mutableListOf<ByteArray>()
    private var pending = ByteArray(0)

    override suspend fun write(data: ByteArray) {
        requests += data.copyOf()
        check(Crc16.isValid(data)) { "Request with bad CRC: ${data.toHex()}" }
        if ((data[0].toInt() and 0xFF) != slaveId) return
        pending = respond(data)
    }

    override suspend fun read(buffer: ByteArray, timeoutMillis: Int): Int {
        val count = minOf(pending.size, buffer.size)
        pending.copyInto(buffer, 0, 0, count)
        pending = pending.copyOfRange(count, pending.size)
        return count
    }

    override suspend fun discardInput() {
        pending = ByteArray(0)
    }

    private fun respond(request: ByteArray): ByteArray {
        val function = request[1].toInt() and 0xFF
        val address = word(request, 2)
        return when (function) {
            0x03 -> {
                val quantity = word(request, 4)
                val addresses = address until address + quantity
                if (addresses.any { it !in registers }) return exception(function, 0x02)
                val data = addresses.flatMap { listOf((registers.getValue(it) ushr 8).toByte(), registers.getValue(it).toByte()) }
                Crc16.appendCrc(byteArrayOf(slaveId.toByte(), 0x03, (quantity * 2).toByte()) + data.toByteArray())
            }
            0x06 -> {
                if (address !in registers) return exception(function, 0x02)
                registers[address] = word(request, 4)
                request.copyOf()
            }
            0x10 -> {
                val quantity = word(request, 4)
                if ((address until address + quantity).any { it !in registers }) return exception(function, 0x02)
                repeat(quantity) { registers[address + it] = word(request, 7 + it * 2) }
                Crc16.appendCrc(request.copyOf(6))
            }
            else -> exception(function, 0x01)
        }
    }

    private fun exception(function: Int, code: Int) =
        Crc16.appendCrc(byteArrayOf(slaveId.toByte(), (function or 0x80).toByte(), code.toByte()))

    private fun word(bytes: ByteArray, offset: Int) =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
}
