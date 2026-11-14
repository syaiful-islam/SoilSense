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
import id.soilsense.modbus.soil.CalibrationSettings
import id.soilsense.modbus.soil.CalibrationWrite
import id.soilsense.modbus.soil.ProbeConfig
import id.soilsense.modbus.soil.SoilSensor
import id.soilsense.modbus.soil.SoilSensorRegisters
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

    /** Keeps searches and probe reconfiguration from overlapping; both switch the port's baud rate. */
    private val probeMutex = Mutex()

    @Volatile
    private var session: Session? = null

    /**
     * Auto-connect is on until the user taps Disconnect, and comes back on when the adapter is
     * unplugged, so plugging the probe in always connects without a tap.
     */
    @Volatile
    private var autoConnectEnabled = true

    private val _connectionState = MutableStateFlow(idleState())
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    init {
        appScope.launch { connector.deviceChanges().collect { onUsbDevicesChanged() } }
        // Adapter already plugged in when the app starts (including launch from the USB "open with" prompt).
        appScope.launch { autoConnect() }
        appScope.launch {
            settingsRepository.settings
                .map { it.baudRate }
                .distinctUntilChanged()
                .drop(1) // the first value is the one the port was (or will be) opened with
                .collect { baudRate -> applyBaudRate(baudRate) }
        }
    }

    override suspend fun connect() {
        autoConnectEnabled = true
        connectLocked()
    }

    private suspend fun connectLocked() = sessionMutex.withLock {
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

    override suspend fun disconnect() {
        // A deliberate disconnect must not be undone by auto-connect while the adapter stays plugged in.
        autoConnectEnabled = false
        sessionMutex.withLock {
            closeSession()
            _connectionState.value = idleState()
        }
    }

    private suspend fun autoConnect() {
        if (!autoConnectEnabled || session != null) return
        val driver = connector.findAdapter() ?: return
        if (!connector.hasPermission(driver.device)) {
            // When the app is in front, Android may be showing its own "open with SoilSense?" prompt
            // for the new device; accepting it grants access. Give it a moment so the user does not
            // get two dialogs, then ask only if access is still missing.
            delay(PERMISSION_PROMPT_GRACE_MILLIS)
            if (!autoConnectEnabled || session != null) return
        }
        connectLocked()
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

    override suspend fun detectSlaveId(): SlaveIdDetection = probeMutex.withLock {
        val current = session ?: return SlaveIdDetection.Failed(SensorReadError.NotConnected)
        val savedBaud = settingsRepository.settings.first().baudRate
        val candidates = listOf(savedBaud) + (SoilSensorRegisters.SUPPORTED_BAUD_RATES - savedBaud)
        var lastError: SensorReadError = SensorReadError.NoResponse
        try {
            for (baud in candidates) {
                current.transport.configure(baud)
                try {
                    return SlaveIdDetection.Found(current.sensor.queryAddress(), baud)
                } catch (e: ModbusException) {
                    lastError = e.toReadError()
                }
            }
            SlaveIdDetection.Failed(lastError)
        } catch (_: IOException) {
            SlaveIdDetection.Failed(onIoFailure(current))
        } finally {
            restoreBaud(current, savedBaud)
        }
    }

    override suspend fun readProbeConfig(): SensorResult<ProbeConfig> =
        withSensor { sensor, slaveId -> sensor.readProbeConfig(slaveId) }

    override suspend fun changeProbeSlaveId(newSlaveId: Int): ProbeChange = probeMutex.withLock {
        val current = session ?: return ProbeChange.Failed(SensorReadError.NotConnected)
        val oldSlaveId = settingsRepository.settings.first().slaveId
        try {
            try {
                current.sensor.writeSlaveId(oldSlaveId, newSlaveId)
            } catch (e: ModbusException.ExceptionResponse) {
                return ProbeChange.Failed(e.toReadError())
            } catch (_: ModbusException) {
                // Some firmwares reply from the new address or not at all; the checks below decide.
            }
            current.sensor.tryReadConfig(newSlaveId)?.takeIf { it.slaveId == newSlaveId }?.let {
                settingsRepository.setSlaveId(newSlaveId)
                return ProbeChange.Verified(it)
            }
            val atOld = current.sensor.tryReadConfig(oldSlaveId)
            if (atOld != null && atOld.slaveId == oldSlaveId) return ProbeChange.Failed(SensorReadError.NoResponse)
            settingsRepository.setSlaveId(newSlaveId)
            ProbeChange.NeedsPowerCycle
        } catch (_: IOException) {
            ProbeChange.Failed(onIoFailure(current))
        }
    }

    override suspend fun changeProbeBaudRate(newBaudRate: Int): ProbeChange = probeMutex.withLock {
        val current = session ?: return ProbeChange.Failed(SensorReadError.NotConnected)
        val settings = settingsRepository.settings.first()
        val oldBaudRate = settings.baudRate
        try {
            try {
                current.sensor.writeBaudRate(settings.slaveId, newBaudRate)
            } catch (e: ModbusException.ExceptionResponse) {
                return ProbeChange.Failed(e.toReadError())
            } catch (_: ModbusException) {
                // The echo may already come back at the new baud rate and look garbled; check below.
            }
            current.transport.configure(newBaudRate)
            current.sensor.tryReadConfig(settings.slaveId)?.let {
                settingsRepository.setBaudRate(newBaudRate)
                _connectionState.value = ConnectionState.Connected(current.info, newBaudRate)
                return ProbeChange.Verified(it)
            }
            current.transport.configure(oldBaudRate)
            val atOld = current.sensor.tryReadConfig(settings.slaveId)
            if (atOld != null && atOld.baudRate == oldBaudRate) return ProbeChange.Failed(SensorReadError.NoResponse)
            // Stored (or unknown): follow the probe; the port switches via the settings observer.
            settingsRepository.setBaudRate(newBaudRate)
            ProbeChange.NeedsPowerCycle
        } catch (_: IOException) {
            ProbeChange.Failed(onIoFailure(current))
        }
    }

    private suspend fun SoilSensor.tryReadConfig(slaveId: Int): ProbeConfig? =
        try {
            readProbeConfig(slaveId)
        } catch (_: ModbusException) {
            null
        }

    /** Puts the port back on [baudRate] after a search; a failure here surfaces on the next request. */
    private suspend fun restoreBaud(current: Session, baudRate: Int) {
        if (session !== current) return
        try {
            current.transport.configure(baudRate)
        } catch (_: IOException) {
            // The adapter is gone; the USB detach handler reports it.
        }
    }

    override suspend fun readNow(): SensorSample = readOnce(settingsRepository.settings.first().slaveId)

    override suspend fun readCalibration(): SensorResult<CalibrationSettings> =
        withSensor { sensor, slaveId -> sensor.readCalibration(slaveId) }

    override suspend fun applyCalibration(change: CalibrationWrite): SensorResult<CalibrationSettings> =
        withSensor { sensor, slaveId -> sensor.apply(slaveId, change) }

    /** Runs one request against the open probe, mapping protocol and USB failures like polling does. */
    private suspend fun <T> withSensor(block: suspend (SoilSensor, Int) -> T): SensorResult<T> {
        val current = session ?: return SensorResult.Failure(SensorReadError.NotConnected)
        val slaveId = settingsRepository.settings.first().slaveId
        return try {
            SensorResult.Success(block(current.sensor, slaveId))
        } catch (e: ModbusException) {
            SensorResult.Failure(e.toReadError())
        } catch (_: IOException) {
            SensorResult.Failure(onIoFailure(current))
        }
    }

    private data class PollingConfig(val connected: Boolean, val slaveId: Int, val intervalMillis: Long)

    private fun poll(config: PollingConfig): Flow<SensorSample> = flow {
        session?.sensor?.reset() // the slave ID may now point at a probe with different firmware
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

    private suspend fun onUsbDevicesChanged() {
        val adapterPresent = sessionMutex.withLock {
            val current = session
            if (current != null) {
                if (!connector.isAttached(current.device)) {
                    closeSession()
                    _connectionState.value = ConnectionState.Failed(ConnectionFailure.CONNECTION_LOST)
                }
                return@withLock current.let { connector.isAttached(it.device) }
            }
            // Not connected: reflect whether an adapter is now plugged in.
            _connectionState.value = idleState()
            connector.findAdapter() != null
        }
        if (adapterPresent) {
            autoConnect()
        } else {
            autoConnectEnabled = true // unplugged: the next plug-in connects again
        }
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

    private companion object {
        const val PERMISSION_PROMPT_GRACE_MILLIS = 1_500L
    }
}
