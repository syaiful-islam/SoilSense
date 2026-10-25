package id.soilsense.app.data.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.Ch34xSerialDriver
import com.hoho.android.usbserial.driver.Cp21xxSerialDriver
import com.hoho.android.usbserial.driver.FtdiSerialDriver
import com.hoho.android.usbserial.driver.ProlificSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber
import dagger.hilt.android.qualifiers.ApplicationContext
import id.soilsense.app.data.model.UsbAdapterInfo
import id.soilsense.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Android-specific USB plumbing: adapter discovery, permission, hot-plug events and opening ports. */
@Singleton
class UsbSerialConnector @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val usbManager: UsbManager = requireNotNull(context.getSystemService(UsbManager::class.java)) {
        "USB host service unavailable"
    }

    /** First attached adapter with a known driver (CH34x, CP210x, FTDI, PL2303, CDC-ACM). */
    fun findAdapter(): UsbSerialDriver? =
        UsbSerialProber.getDefaultProber().findAllDrivers(usbManager).firstOrNull()

    fun describe(driver: UsbSerialDriver): UsbAdapterInfo = UsbAdapterInfo(
        chipName = when (driver) {
            is Ch34xSerialDriver -> "CH34x"
            is Cp21xxSerialDriver -> "CP210x"
            is FtdiSerialDriver -> "FTDI"
            is ProlificSerialDriver -> "PL2303"
            is CdcAcmSerialDriver -> "CDC-ACM"
            else -> "USB serial"
        },
        vendorId = driver.device.vendorId,
        productId = driver.device.productId,
    )

    fun isAttached(device: UsbDevice): Boolean =
        usbManager.deviceList.values.any { it.deviceName == device.deviceName }

    /** Emits whenever a USB device is attached or detached. */
    fun deviceChanges(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        // Protected system broadcasts still reach non-exported receivers.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    /** Shows the system USB permission dialog if needed; returns whether access was granted. */
    suspend fun requestPermission(device: UsbDevice): Boolean {
        if (usbManager.hasPermission(device)) return true
        return suspendCancellableCoroutine { continuation ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action != ACTION_USB_PERMISSION) return
                    runCatching { context.unregisterReceiver(this) }
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (continuation.isActive) continuation.resume(granted)
                }
            }
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(ACTION_USB_PERMISSION),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            continuation.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }

            // The system fills in EXTRA_PERMISSION_GRANTED, so the PendingIntent must be mutable; since
            // Android 14 a mutable PendingIntent must be explicit, hence setPackage().
            val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            usbManager.requestPermission(device, PendingIntent.getBroadcast(context, 0, intent, flags))
        }
    }

    /** Opens port 0 of [driver] at [baudRate], 8N1. Requires USB permission. */
    @Throws(IOException::class)
    suspend fun open(driver: UsbSerialDriver, baudRate: Int): UsbSerialPortTransport {
        val port = driver.ports.first()
        withContext(ioDispatcher) {
            val connection = usbManager.openDevice(driver.device)
                ?: throw IOException("openDevice() returned null for ${driver.device.deviceName}")
            try {
                port.open(connection)
            } catch (e: IOException) {
                connection.close()
                throw e
            }
        }
        val transport = UsbSerialPortTransport(port, ioDispatcher)
        try {
            transport.configure(baudRate)
        } catch (e: IOException) {
            transport.close()
            throw e
        } catch (e: UnsupportedOperationException) {
            transport.close()
            throw IOException("Adapter does not support $baudRate baud", e)
        }
        return transport
    }

    private companion object {
        const val ACTION_USB_PERMISSION = "id.soilsense.app.USB_PERMISSION"
    }
}
