package id.soilsense.modbus

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

/**
 * Scripted RS-485 bus: every written request is answered with the next queued reply,
 * delivered in [chunkSize]-byte pieces to exercise frame reassembly.
 */
class FakeSerialTransport(
    private val timeSource: TestTimeSource,
    private val chunkSize: Int = Int.MAX_VALUE,
) : SerialTransport {

    val writtenFrames = mutableListOf<ByteArray>()
    private val replies = ArrayDeque<ByteArray?>()
    private var pending = ByteArray(0)
    var discardCount = 0
        private set

    /** Queue the reply for the next request; `null` means the slave stays silent. */
    fun enqueueReply(reply: ByteArray?) {
        replies.addLast(reply)
    }

    /** Bytes already sitting in the receive buffer before the next request (e.g. a late reply). */
    fun injectStaleInput(bytes: ByteArray) {
        pending += bytes
    }

    override suspend fun write(data: ByteArray) {
        writtenFrames += data.copyOf()
        replies.removeFirstOrNull()?.let { pending += it }
    }

    override suspend fun read(buffer: ByteArray, timeoutMillis: Int): Int {
        if (pending.isEmpty()) {
            timeSource += timeoutMillis.milliseconds
            return 0
        }
        val count = minOf(pending.size, buffer.size, chunkSize)
        pending.copyInto(buffer, 0, 0, count)
        pending = pending.copyOfRange(count, pending.size)
        return count
    }

    override suspend fun discardInput() {
        discardCount++
        pending = ByteArray(0)
    }
}
