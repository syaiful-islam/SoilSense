package id.soilsense.modbus

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** Computes the full length of a normal (non-exception) reply from the bytes received so far, or null if unknown yet. */
internal typealias FrameLength = (frame: ByteArray, length: Int) -> Int?

/**
 * Minimal Modbus RTU master for a half-duplex RS-485 bus.
 *
 * Transactions are serialised with a mutex because RS-485 is a shared medium: a second request sent
 * before the first reply arrives would collide on the wire.
 */
public class ModbusRtuClient(
    private val transport: SerialTransport,
    private val responseTimeout: Duration = DEFAULT_RESPONSE_TIMEOUT,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val mutex = Mutex()

    /** Function 0x03. Returns each register as an unsigned 16-bit value (0..65535). */
    public suspend fun readHoldingRegisters(slaveId: Int, startAddress: Int, quantity: Int): IntArray {
        val request = ModbusFrames.readHoldingRegisters(slaveId, startAddress, quantity)
        val response = transact(
            request = request,
            slaveId = slaveId,
            functionCode = ModbusFrames.FC_READ_HOLDING_REGISTERS,
            // The reply length is announced by its byte-count field, so frame on that rather than
            // on the requested quantity; a mismatching count is then reported as such, not as a timeout.
            normalLength = { frame, length ->
                if (length > HEADER_LENGTH) {
                    HEADER_LENGTH + 1 + (frame[HEADER_LENGTH].toInt() and 0xFF) + CRC_LENGTH
                } else {
                    null
                }
            },
        )
        val byteCount = response[2].toInt() and 0xFF
        if (byteCount != quantity * 2) {
            throw ModbusException.UnexpectedResponse("Expected ${quantity * 2} data bytes, got $byteCount")
        }
        return IntArray(quantity) { index ->
            val offset = HEADER_LENGTH + 1 + index * 2
            ((response[offset].toInt() and 0xFF) shl 8) or (response[offset + 1].toInt() and 0xFF)
        }
    }

    /** Function 0x06. A successful write is echoed back unchanged by the slave. */
    public suspend fun writeSingleRegister(slaveId: Int, address: Int, value: Int) {
        val request = ModbusFrames.writeSingleRegister(slaveId, address, value)
        val response = transact(
            request = request,
            slaveId = slaveId,
            functionCode = ModbusFrames.FC_WRITE_SINGLE_REGISTER,
            normalLength = { _, _ -> request.size },
        )
        if (!response.contentEquals(request)) {
            throw ModbusException.UnexpectedResponse("Write echo ${response.toHex()} does not match request")
        }
    }

    private suspend fun transact(
        request: ByteArray,
        slaveId: Int,
        functionCode: Int,
        normalLength: FrameLength,
    ): ByteArray = mutex.withLock {
        transport.discardInput()
        transport.write(request)
        readResponse(slaveId, functionCode, normalLength)
    }

    private suspend fun readResponse(slaveId: Int, functionCode: Int, normalLength: FrameLength): ByteArray {
        val deadline = timeSource.markNow() + responseTimeout
        val frame = ByteArray(MAX_FRAME_LENGTH)
        val chunk = ByteArray(READ_CHUNK_SIZE)
        var length = 0

        while (true) {
            val expectedLength = expectedFrameLength(frame, length, functionCode, normalLength)
            if (expectedLength != null && length >= expectedLength) {
                return validate(frame.copyOf(expectedLength), functionCode)
            }

            val remaining = -deadline.elapsedNow()
            if (remaining <= Duration.ZERO) throw ModbusException.ResponseTimeout(length)

            val timeoutMillis = remaining.inWholeMilliseconds.coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
            val read = transport.read(chunk, timeoutMillis)
            for (i in 0 until read) {
                // Some USB-RS485 adapters emit a stray byte when the driver switches direction;
                // skip anything before the slave address so the frame stays aligned.
                if (length == 0 && (chunk[i].toInt() and 0xFF) != slaveId) continue
                if (length < frame.size) frame[length++] = chunk[i]
            }
        }
    }

    private fun expectedFrameLength(
        frame: ByteArray,
        length: Int,
        functionCode: Int,
        normalLength: FrameLength,
    ): Int? {
        if (length < HEADER_LENGTH) return null
        return when (frame[1].toInt() and 0xFF) {
            functionCode -> normalLength(frame, length)
            functionCode or EXCEPTION_FLAG -> EXCEPTION_FRAME_LENGTH
            else -> throw ModbusException.UnexpectedResponse(
                "Unexpected function code in response ${frame.toHex(length)}",
            )
        }
    }

    private fun validate(frame: ByteArray, functionCode: Int): ByteArray {
        if (!Crc16.isValid(frame)) throw ModbusException.CrcMismatch(frame)
        if ((frame[1].toInt() and 0xFF) == functionCode or EXCEPTION_FLAG) {
            throw ModbusException.ExceptionResponse(functionCode, frame[2].toInt() and 0xFF)
        }
        return frame
    }

    public companion object {
        /** Datasheet NPK response time is < 1 s; at 4800 baud a 23-byte reply takes ~48 ms on the wire. */
        public val DEFAULT_RESPONSE_TIMEOUT: Duration = 1_000.milliseconds

        private const val HEADER_LENGTH = 2 // slave address + function code
        private const val CRC_LENGTH = 2
        private const val EXCEPTION_FRAME_LENGTH = 5
        private const val EXCEPTION_FLAG = 0x80

        // Address + function + byte count + 255 data bytes + CRC; Modbus RTU frames never exceed 256 bytes.
        private const val MAX_FRAME_LENGTH = 260

        // usb-serial-for-android requires read buffers of at least the endpoint packet size (64 bytes).
        private const val READ_CHUNK_SIZE = 256
    }
}
