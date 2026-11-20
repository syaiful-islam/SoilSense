package id.soilsense.app.testing

import android.net.Uri
import id.soilsense.app.data.history.ExportResult
import id.soilsense.app.data.history.HistoryRepository
import id.soilsense.app.data.model.ConnectionState
import id.soilsense.app.data.model.RecordedReading
import id.soilsense.app.data.model.SensorSample
import id.soilsense.app.data.model.SensorSettings
import id.soilsense.app.data.model.UsbAdapterInfo
import id.soilsense.app.data.model.SensorReadError
import id.soilsense.app.data.sensor.SensorResult
import id.soilsense.app.data.sensor.ProbeChange
import id.soilsense.app.data.sensor.ProbeSearch
import id.soilsense.app.data.sensor.SoilSensorRepository
import id.soilsense.app.data.settings.SettingsRepository
import id.soilsense.modbus.soil.CalibrationSettings
import id.soilsense.modbus.soil.CalibrationWrite
import id.soilsense.modbus.soil.ProbeConfig
import id.soilsense.modbus.soil.FactorParameter
import id.soilsense.modbus.soil.NpkCalibration
import id.soilsense.modbus.soil.Nutrient
import id.soilsense.modbus.soil.OffsetParameter
import id.soilsense.modbus.soil.SoilReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

val factoryCalibration = CalibrationSettings(
    offsets = OffsetParameter.entries.associateWith { 0 },
    factors = FactorParameter.entries.associateWith { it.defaultRaw },
    npk = Nutrient.entries.associateWith { NpkCalibration.DEFAULT },
)

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

    /** What [searchProbe] emits; replace with a never-ending flow to test cancelling. */
    var searchFlow: Flow<ProbeSearch> = flowOf(ProbeSearch.Asking(4800), ProbeSearch.Found(1, 4800))

    override fun searchProbe(): Flow<ProbeSearch> = searchFlow

    var factoryResult: ProbeChange = ProbeChange.Verified(ProbeConfig(slaveId = 1, baudRate = 4800))
    var factoryResets = 0
        private set

    override suspend fun restoreProbeFactorySettings(): ProbeChange {
        factoryResets++
        return factoryResult
    }

    var probeConfig: ProbeConfig = ProbeConfig(slaveId = 1, baudRate = 4800)
    var probeReadFailure: SensorReadError? = null

    /** Null: the change succeeds and [probeConfig] is updated, like a real probe answering at once. */
    var probeChangeResult: ProbeChange? = null
    val probeChanges = mutableListOf<String>()

    override suspend fun readProbeConfig(): SensorResult<ProbeConfig> =
        probeReadFailure?.let { SensorResult.Failure(it) } ?: SensorResult.Success(probeConfig)

    override suspend fun changeProbeSlaveId(newSlaveId: Int): ProbeChange {
        probeChanges += "id=$newSlaveId"
        return probeChangeResult ?: ProbeChange.Verified(probeConfig.copy(slaveId = newSlaveId).also { probeConfig = it })
    }

    override suspend fun changeProbeBaudRate(newBaudRate: Int): ProbeChange {
        probeChanges += "baud=$newBaudRate"
        return probeChangeResult ?: ProbeChange.Verified(probeConfig.copy(baudRate = newBaudRate).also { probeConfig = it })
    }

    var nextReading: SensorSample = SensorSample.Success(testReading, timestampMillis = 0)
    var calibration: CalibrationSettings = factoryCalibration
    var failure: SensorReadError? = null
    val appliedWrites = mutableListOf<CalibrationWrite>()

    override suspend fun readNow(): SensorSample = nextReading

    override suspend fun readCalibration(): SensorResult<CalibrationSettings> =
        failure?.let { SensorResult.Failure(it) } ?: SensorResult.Success(calibration)

    override suspend fun applyCalibration(change: CalibrationWrite): SensorResult<CalibrationSettings> {
        failure?.let { return SensorResult.Failure(it) }
        appliedWrites += change
        calibration = when (change) {
            is CalibrationWrite.Offset -> calibration.copy(offsets = calibration.offsets + (change.parameter to change.raw))
            is CalibrationWrite.Factor -> calibration.copy(factors = calibration.factors + (change.parameter to change.raw))
            is CalibrationWrite.Npk -> calibration.copy(npk = calibration.npk + (change.nutrient to change.calibration))
            CalibrationWrite.ResetNpk -> calibration.copy(npk = factoryCalibration.npk)
            CalibrationWrite.RestoreDefaults -> factoryCalibration
        }
        return SensorResult.Success(calibration)
    }
}

class FakeSettingsRepository(initial: SensorSettings = SensorSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    val current: SensorSettings get() = state.value
    override val settings: StateFlow<SensorSettings> = state

    override suspend fun setSlaveId(slaveId: Int) = state.update { it.copy(slaveId = slaveId) }

    override suspend fun setBaudRate(baudRate: Int) = state.update { it.copy(baudRate = baudRate) }

    override suspend fun setPollIntervalSeconds(seconds: Int) = state.update { it.copy(pollIntervalSeconds = seconds) }

    override suspend fun setUseWallpaperColors(enabled: Boolean) = state.update { it.copy(useWallpaperColors = enabled) }

}

class FakeHistoryRepository : HistoryRepository {
    private val rows = MutableStateFlow<List<RecordedReading>>(emptyList())
    val saved: List<RecordedReading> get() = rows.value

    override fun observeLatest(limit: Int): Flow<List<RecordedReading>> =
        rows.map { list -> list.sortedByDescending { it.timestampMillis }.take(limit) }

    override fun observeCount(): Flow<Int> = rows.map { it.size }

    override suspend fun save(timestampMillis: Long, reading: SoilReading, name: String) =
        rows.update {
            it + RecordedReading(id = it.size + 1L, timestampMillis = timestampMillis, reading = reading, name = name)
        }

    override suspend fun clear() = rows.update { emptyList() }

    override suspend fun exportCsv(destination: Uri): ExportResult = ExportResult.Success(rows.value.size)
}
