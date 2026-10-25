package id.soilsense.app.data.sensor

import android.hardware.usb.UsbDevice
import id.soilsense.app.data.WallClock
import id.soilsense.app.data.model.ConnectionFailure
import id.soilsense.app.data.model.ConnectionState
import id.soilsense.app.data.model.SensorReadError
import id.soilsense.app.data.model.SensorSample
import id.soilsense.app.data.model.UsbAdapterInfo
import id.soilsense.app.data.settings.SettingsRepository
import id.soilsense.app.data.usb.UsbSerialConnector
import id.soilsense.app.data.usb.UsbSerialPortTransport
import id.soilsense.app.di.ApplicationScope
import id.soilsense.modbus.ModbusException
import id.soilsense.modbus.ModbusRtuClient
import id.soilsense.modbus.soil.SoilSensor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsbSoilSensorRepository @Inject constructor(
    private val connector: UsbSerialConnector,
    private val settingsRepository: SettingsRepository,
    private val clock: WallClock,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : SoilSensorRepository {

    private class Session(
        val device: UsbDevice,
        val info: UsbAdapterInfo,
        val transport: UsbSerialPortTransport,
        val sensor: SoilSensor,
    )

    private val sessionMutex = Mutex()

    @Volatile
    private var session: Session? = null

    private val _connectionState = MutableStateFlow(idleState())
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    init {
        appScope.launch { connector.deviceChanges().collect { onUsbDevicesChanged() } }
        appScope.launch {
            settingsRepository.settings
                .map { it.baudRate }
                .distinctUntilChanged()
                .drop(1) // the first value is the one the port was (or will be) opened with
                .collect { baudRate -> applyBaudRate(baudRate) }
        }
    }

    override suspend fun connect() = sessionMutex.withLock {
        if (session != null) return@withLock

        val driver = connector.findAdapter()
        if (driver == null) {
            _connectionState.value = ConnectionState.NoDevice
            return@withLock
        }

        _connectionState.value = ConnectionState.RequestingPermission
        if (!connector.requestPermission(driver.device)) {
            _connectionState.value = ConnectionState.PermissionDenied
            return@withLock
        }

        _connectionState.value = ConnectionState.Connecting
        val baudRate = settingsRepository.settings.first().baudRate
        val transport = try {
            connector.open(driver, baudRate)
        } catch (_: IOException) {
            _connectionState.value = ConnectionState.Failed(ConnectionFailure.OPEN_FAILED)
            return@withLock
        }

        val info = connector.describe(driver)
        session = Session(
            device = driver.device,
            info = info,
            transport = transport,
            sensor = SoilSensor(ModbusRtuClient(transport)),
        )
        _connectionState.value = ConnectionState.Connected(info, baudRate)
    }

    override suspend fun disconnect() = sessionMutex.withLock {
        closeSession()
        _connectionState.value = idleState()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun liveReadings(): Flow<SensorSample> =
        combine(connectionState, settingsRepository.settings) { state, settings ->
            PollingConfig(
                connected = state is ConnectionState.Connected,
                slaveId = settings.slaveId,
                intervalMillis = settings.pollIntervalSeconds * 1_000L,
            )
        }
            .distinctUntilChanged()
            .flatMapLatest { config -> if (config.connected) poll(config) else emptyFlow() }

    override suspend fun detectSlaveId(): SlaveIdDetection {
        val current = session ?: return SlaveIdDetection.Failed(SensorReadError.NotConnected)
        return try {
            SlaveIdDetection.Found(current.sensor.queryAddress())
        } catch (e: ModbusException) {
            SlaveIdDetection.Failed(e.toReadError())
        } catch (_: IOException) {
            SlaveIdDetection.Failed(onIoFailure(current))
        }
    }

    private data class PollingConfig(val connected: Boolean, val slaveId: Int, val intervalMillis: Long)

    private fun poll(config: PollingConfig): Flow<SensorSample> = flow {
        session?.sensor?.reset() // the slave ID may now point at a different probe
        while (currentCoroutineContext().isActive) {
            val startedAt = clock.nowMillis()
            val sample = readOnce(config.slaveId)
            emit(sample)
            if (sample is SensorSample.Failure && sample.error.endsSession()) break
            val elapsed = clock.nowMillis() - startedAt
            delay((config.intervalMillis - elapsed).coerceAtLeast(0))
        }
    }

    private suspend fun readOnce(slaveId: Int): SensorSample {
        val current = session ?: return SensorSample.Failure(SensorReadError.NotConnected, clock.nowMillis())
        return try {
            SensorSample.Success(current.sensor.read(slaveId), clock.nowMillis())
        } catch (e: ModbusException) {
            SensorSample.Failure(e.toReadError(), clock.nowMillis())
        } catch (_: IOException) {
            SensorSample.Failure(onIoFailure(current), clock.nowMillis())
        }
    }

    /**
     * An I/O error either means the adapter vanished, or that [disconnect]/a detach event closed the
     * port underneath an in-flight read. Only the former is reported as a lost connection.
     */
    private suspend fun onIoFailure(failed: Session): SensorReadError = sessionMutex.withLock {
        if (session !== failed) return@withLock SensorReadError.NotConnected
        closeSession()
        _connectionState.value = ConnectionState.Failed(ConnectionFailure.CONNECTION_LOST)
        SensorReadError.ConnectionLost
    }

    private suspend fun onUsbDevicesChanged() = sessionMutex.withLock {
        val current = session
        if (current != null) {
            if (!connector.isAttached(current.device)) {
                closeSession()
                _connectionState.value = ConnectionState.Failed(ConnectionFailure.CONNECTION_LOST)
            }
            return@withLock
        }
        // Not connected: reflect whether an adapter is now plugged in.
        _connectionState.value = idleState()
    }

    private suspend fun applyBaudRate(baudRate: Int) = sessionMutex.withLock {
        val current = session ?: return@withLock
        try {
            current.transport.configure(baudRate)
            _connectionState.value = ConnectionState.Connected(current.info, baudRate)
        } catch (_: IOException) {
            closeSession()
            _connectionState.value = ConnectionState.Failed(ConnectionFailure.CONNECTION_LOST)
        } catch (_: UnsupportedOperationException) {
            closeSession()
            _connectionState.value = ConnectionState.Failed(ConnectionFailure.OPEN_FAILED)
        }
    }

    /** Caller must hold [sessionMutex]. */
    private suspend fun closeSession() {
        val current = session ?: return
        session = null
        current.transport.close()
    }

    private fun idleState(): ConnectionState =
        connector.findAdapter()?.let { ConnectionState.Disconnected(connector.describe(it)) }
            ?: ConnectionState.NoDevice

    private fun SensorReadError.endsSession(): Boolean =
        this == SensorReadError.ConnectionLost || this == SensorReadError.NotConnected

    private fun ModbusException.toReadError(): SensorReadError = when (this) {
        is ModbusException.ResponseTimeout -> SensorReadError.NoResponse
        is ModbusException.CrcMismatch, is ModbusException.UnexpectedResponse -> SensorReadError.CorruptedResponse
        is ModbusException.ExceptionResponse -> SensorReadError.DeviceException(exceptionCode)
    }
}
