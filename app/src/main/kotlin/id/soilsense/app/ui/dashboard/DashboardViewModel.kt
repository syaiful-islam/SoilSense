package id.soilsense.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.soilsense.app.data.history.HistoryRepository
import id.soilsense.app.data.model.ConnectionState
import id.soilsense.app.data.model.SensorReadError
import id.soilsense.app.data.model.SensorSample
import id.soilsense.app.data.sensor.SoilSensorRepository
import id.soilsense.app.data.settings.SettingsRepository
import id.soilsense.modbus.soil.SoilReading
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val connection: ConnectionState = ConnectionState.NoDevice,
    val latestReading: SoilReading? = null,
    val lastUpdatedMillis: Long? = null,
    /** Error of the most recent poll; cleared by the next successful one. */
    val lastError: SensorReadError? = null,
    val isRecording: Boolean = false,
    val pollIntervalSeconds: Int = 0,
)

/** Accumulated poll results, independent of connection and settings. */
private data class SampleState(
    val latestReading: SoilReading? = null,
    val lastUpdatedMillis: Long? = null,
    val lastError: SensorReadError? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val sensorRepository: SoilSensorRepository,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    private val samples = sensorRepository.liveReadings()
        .onEach { sample ->
            if (sample is SensorSample.Success && settingsRepository.settings.first().recordingEnabled) {
                historyRepository.save(sample.timestampMillis, sample.reading)
            }
        }
        .scan(SampleState()) { state, sample ->
            when (sample) {
                is SensorSample.Success -> SampleState(
                    latestReading = sample.reading,
                    lastUpdatedMillis = sample.timestampMillis,
                    lastError = null,
                )
                // Keep the last good values on screen; the error banner says they are stale.
                is SensorSample.Failure -> state.copy(lastError = sample.error)
            }
        }

    val uiState: StateFlow<DashboardUiState> = combine(
        sensorRepository.connectionState,
        samples,
        settingsRepository.settings,
    ) { connection, sampleState, settings ->
        DashboardUiState(
            connection = connection,
            latestReading = sampleState.latestReading,
            lastUpdatedMillis = sampleState.lastUpdatedMillis,
            lastError = sampleState.lastError.takeIf { connection is ConnectionState.Connected },
            isRecording = settings.recordingEnabled,
            pollIntervalSeconds = settings.pollIntervalSeconds,
        )
    }.stateIn(
        scope = viewModelScope,
        // Polling lives inside this subscription: it stops 5 s after the screen goes away and
        // survives configuration changes.
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(connection = sensorRepository.connectionState.value),
    )

    fun connect() {
        viewModelScope.launch { sensorRepository.connect() }
    }

    fun disconnect() {
        viewModelScope.launch { sensorRepository.disconnect() }
    }

    fun setRecording(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setRecordingEnabled(enabled) }
    }
}
