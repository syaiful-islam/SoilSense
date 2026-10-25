package id.soilsense.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.soilsense.app.data.model.ConnectionState
import id.soilsense.app.data.model.SensorReadError
import id.soilsense.app.data.model.SensorSettings
import id.soilsense.app.data.sensor.SlaveIdDetection
import id.soilsense.app.data.sensor.SoilSensorRepository
import id.soilsense.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DetectionState {
    data object Idle : DetectionState

    data object Running : DetectionState

    data class Found(val slaveId: Int) : DetectionState

    data class Failed(val error: SensorReadError) : DetectionState
}

data class SettingsUiState(
    val settings: SensorSettings? = null,
    val isConnected: Boolean = false,
    val detection: DetectionState = DetectionState.Idle,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val sensorRepository: SoilSensorRepository,
) : ViewModel() {

    private val detection = MutableStateFlow<DetectionState>(DetectionState.Idle)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        sensorRepository.connectionState,
        detection,
    ) { settings, connection, detectionState ->
        SettingsUiState(
            settings = settings,
            isConnected = connection is ConnectionState.Connected,
            detection = detectionState,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Ignores out-of-range input; the screen validates before calling. */
    fun setSlaveId(slaveId: Int) {
        if (slaveId !in SensorSettings.SLAVE_ID_RANGE) return
        viewModelScope.launch { settingsRepository.setSlaveId(slaveId) }
    }

    fun setBaudRate(baudRate: Int) {
        viewModelScope.launch { settingsRepository.setBaudRate(baudRate) }
    }

    fun setPollInterval(seconds: Int) {
        val clamped = seconds.coerceIn(SensorSettings.POLL_INTERVAL_RANGE_SECONDS)
        viewModelScope.launch { settingsRepository.setPollIntervalSeconds(clamped) }
    }

    fun detectSlaveId() {
        if (detection.value == DetectionState.Running) return
        viewModelScope.launch {
            detection.value = DetectionState.Running
            detection.value = when (val result = sensorRepository.detectSlaveId()) {
                is SlaveIdDetection.Found -> DetectionState.Found(result.slaveId)
                is SlaveIdDetection.Failed -> DetectionState.Failed(result.error)
            }
        }
    }

    fun applyDetectedSlaveId() {
        val found = detection.value as? DetectionState.Found ?: return
        setSlaveId(found.slaveId)
        detection.value = DetectionState.Idle
    }
}
