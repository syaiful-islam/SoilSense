package id.soilsense.modbus

/** Failures of a single Modbus RTU transaction. */
public sealed class ModbusException(message: String) : Exception(message) {

    /** The slave did not answer (or answered incompletely) within the response timeout. */
    public class ResponseTimeout(public val receivedBytes: Int) :
        ModbusException("No complete response within timeout ($receivedBytes bytes received)")

    /** A complete frame arrived but its CRC is wrong — usually noise, a wrong baud rate or swapped A/B lines. */
    public class CrcMismatch(public val frame: ByteArray) :
        ModbusException("CRC mismatch in response ${frame.toHex()}")

    /** A frame with a valid CRC arrived but does not match the request (wrong slave, function or length). */
    public class UnexpectedResponse(detail: String) : ModbusException(detail)

    /** The slave answered with a Modbus exception response. */
    public class ExceptionResponse(
        public val functionCode: Int,
        public val exceptionCode: Int,
    ) : ModbusException("Slave returned exception code $exceptionCode for function $functionCode") {
        public companion object {
            public const val ILLEGAL_FUNCTION: Int = 0x01
            public const val ILLEGAL_DATA_ADDRESS: Int = 0x02
            public const val ILLEGAL_DATA_VALUE: Int = 0x03
            public const val SLAVE_DEVICE_FAILURE: Int = 0x04
        }
    }
}

internal fun ByteArray.toHex(length: Int = size): String =
    take(length).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
