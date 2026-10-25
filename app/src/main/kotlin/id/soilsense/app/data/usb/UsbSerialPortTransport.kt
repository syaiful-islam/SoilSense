package id.soilsense.app.data.usb

import com.hoho.android.usbserial.driver.UsbSerialPort
import id.soilsense.modbus.SerialTransport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * [SerialTransport] over an open usb-serial-for-android port.
 *
 * The library's read/write calls block, so every call hops to the injected I/O dispatcher.
 * Auto-direction RS-485 adapters (like the CH340 + transceiver dongles) need no DTR/RTS handling.
 */
class UsbSerialPortTransport(
    private val port: UsbSerialPort,
    private val ioDispatcher: CoroutineDispatcher,
) : SerialTransport {

    @Throws(IOException::class)
    suspend fun configure(baudRate: Int) = withContext(ioDispatcher) {
        port.setParameters(baudRate, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
    }

    override suspend fun write(data: ByteArray) = withContext(ioDispatcher) {
        port.write(data, WRITE_TIMEOUT_MILLIS)
    }

    override suspend fun read(buffer: ByteArray, timeoutMillis: Int): Int = withContext(ioDispatcher) {
        port.read(buffer, timeoutMillis)
    }

    override suspend fun discardInput() = withContext(ioDispatcher) {
        // purgeHwBuffers() is not implemented by every driver (CH34x included), so drain in software.
        val scratch = ByteArray(DRAIN_BUFFER_SIZE)
        var iterations = 0
        while (iterations++ < MAX_DRAIN_ITERATIONS && port.read(scratch, DRAIN_TIMEOUT_MILLIS) > 0) {
            // Keep reading until the line is quiet.
        }
    }

    suspend fun close() = withContext(ioDispatcher) {
        try {
            port.close()
        } catch (_: IOException) {
            // Already closed or the device is gone; nothing left to release.
        }
    }

    private companion object {
        const val WRITE_TIMEOUT_MILLIS = 500
        const val DRAIN_TIMEOUT_MILLIS = 20
        const val DRAIN_BUFFER_SIZE = 256
        const val MAX_DRAIN_ITERATIONS = 16
    }
}
