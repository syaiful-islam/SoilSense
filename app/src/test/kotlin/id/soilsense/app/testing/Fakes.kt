package id.soilsense.app.testing

import android.net.Uri
import id.soilsense.app.data.history.ExportResult
import id.soilsense.app.data.history.HistoryRepository
import id.soilsense.app.data.model.ConnectionState
import id.soilsense.app.data.model.RecordedReading
import id.soilsense.app.data.model.SensorSample
import id.soilsense.app.data.model.SensorSettings
import id.soilsense.app.data.model.UsbAdapterInfo
import id.soilsense.app.data.sensor.SlaveIdDetection
import id.soilsense.app.data.sensor.SoilSensorRepository
import id.soilsense.app.data.settings.SettingsRepository
import id.soilsense.modbus.soil.SoilReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

val testAdapter = UsbAdapterInfo(chipName = "CH34x", vendorId = 0x1A86, productId = 0x7523)

val testReading = SoilReading(
    moisturePercent = 46.4,
    temperatureCelsius = 33.2,
    conductivityMicroSiemensPerCm = 44,
    ph = 9.0,
    nitrogenMgPerKg = 32,
    phosphorusMgPerKg = 88,
    potassiumMgPerKg = 104,
)

class FakeSoilSensorRepository : SoilSensorRepository {
    override val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected(testAdapter))
    private val samples = MutableSharedFlow<SensorSample>()
    var detectionResult: SlaveIdDetection = SlaveIdDetection.Found(1)
    var connectCalls = 0
        private set

    suspend fun emit(sample: SensorSample) = samples.emit(sample)

    override suspend fun connect() {
        connectCalls++
        connectionState.value = ConnectionState.Connected(testAdapter, 4800)
    }

    override suspend fun disconnect() {
        connectionState.value = ConnectionState.Disconnected(testAdapter)
    }

    override fun liveReadings(): Flow<SensorSample> = samples

    override suspend fun detectSlaveId(): SlaveIdDetection = detectionResult
}

class FakeSettingsRepository(initial: SensorSettings = SensorSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    val current: SensorSettings get() = state.value
    override val settings: StateFlow<SensorSettings> = state

    override suspend fun setSlaveId(slaveId: Int) = state.update { it.copy(slaveId = slaveId) }

    override suspend fun setBaudRate(baudRate: Int) = state.update { it.copy(baudRate = baudRate) }

    override suspend fun setPollIntervalSeconds(seconds: Int) = state.update { it.copy(pollIntervalSeconds = seconds) }

    override suspend fun setRecordingEnabled(enabled: Boolean) = state.update { it.copy(recordingEnabled = enabled) }
}

class FakeHistoryRepository : HistoryRepository {
    private val rows = MutableStateFlow<List<RecordedReading>>(emptyList())
    val saved: List<RecordedReading> get() = rows.value

    override fun observeLatest(limit: Int): Flow<List<RecordedReading>> =
        rows.map { list -> list.sortedByDescending { it.timestampMillis }.take(limit) }

    override fun observeCount(): Flow<Int> = rows.map { it.size }

    override suspend fun save(timestampMillis: Long, reading: SoilReading) =
        rows.update { it + RecordedReading(id = it.size + 1L, timestampMillis = timestampMillis, reading = reading) }

    override suspend fun clear() = rows.update { emptyList() }

    override suspend fun exportCsv(destination: Uri): ExportResult = ExportResult.Success(rows.value.size)
}
