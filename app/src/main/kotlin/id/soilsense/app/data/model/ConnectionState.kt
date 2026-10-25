package id.soilsense.app.data.model

/** State of the link between the phone and the USB-RS485 adapter. */
sealed interface ConnectionState {

    /** No supported USB serial adapter is attached. */
    data object NoDevice : ConnectionState

    /** An adapter is attached but no port is open. */
    data class Disconnected(val device: UsbAdapterInfo) : ConnectionState

    data object RequestingPermission : ConnectionState

    data object PermissionDenied : ConnectionState

    data object Connecting : ConnectionState

    data class Connected(val device: UsbAdapterInfo, val baudRate: Int) : ConnectionState

    data class Failed(val reason: ConnectionFailure) : ConnectionState
}

enum class ConnectionFailure {
    /** The USB port could not be opened or configured. */
    OPEN_FAILED,

    /** The adapter was unplugged or the USB transfer failed while connected. */
    CONNECTION_LOST,
}

/** Human-readable identification of the adapter, e.g. "CH34x" with VID/PID 1A86:7523. */
data class UsbAdapterInfo(
    val chipName: String,
    val vendorId: Int,
    val productId: Int,
) {
    val usbId: String get() = "%04X:%04X".format(vendorId, productId)
}
