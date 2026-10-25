package id.soilsense.modbus

/**
 * Byte pipe to a half-duplex RS-485 bus. Implementations decide which thread blocking I/O runs on.
 */
public interface SerialTransport {

    /** Writes the whole [data] frame to the bus. */
    public suspend fun write(data: ByteArray)

    /**
     * Reads up to `buffer.size` bytes into [buffer], waiting at most [timeoutMillis].
     *
     * @return number of bytes read; `0` when nothing arrived within the timeout.
     */
    public suspend fun read(buffer: ByteArray, timeoutMillis: Int): Int

    /** Drops any bytes already received, so a late reply to an earlier request cannot be mistaken for the next one. */
    public suspend fun discardInput()
}
