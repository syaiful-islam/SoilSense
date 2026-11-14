package id.soilsense.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.soilsense.app.data.model.ConnectionState
import id.soilsense.app.data.model.SensorReadError
import id.soilsense.app.data.model.SensorSettings
import id.soilsense.app.data.sensor.ProbeChange
import id.soilsense.app.data.sensor.SensorResult
import id.soilsense.app.data.sensor.SlaveIdDetection
import id.soilsense.app.data.sensor.SoilSensorRepository
import id.soilsense.app.data.settings.SettingsRepository
import id.soilsense.modbus.soil.ProbeConfig
import id.soilsense.modbus.soil.SoilSensorRegisters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DetectionState {
    data object Idle : DetectionState

    data object Running : DetectionState

    data class Found(val slaveId: Int, val baudRate: Int) : DetectionState

    data class Failed(val error: SensorReadError) : DetectionState
}

/** A change to the probe's own settings, waiting for the user's confirmation. */
sealed interface ProbeConfigProposal {
    data class SlaveId(val from: Int, val to: Int) : ProbeConfigProposal

    data class BaudRate(val from: Int, val to: Int) : ProbeConfigProposal
}

/** Last outcome shown in the "Configuration in the probe" card. */
sealed interface ProbeConfigMessage {
    data object Verified : ProbeConfigMessage

    data object NeedsPowerCycle : ProbeConfigMessage

    data class WriteFailed(val error: SensorReadError) : ProbeConfigMessage

    data class ReadFailed(val error: SensorReadError) : ProbeConfigMessage
}

data class ProbeConfigState(
    /** What the probe reports; null until read successfully. */
    val config: ProbeConfig? = null,
    val isLoading: Boolean = false,
    val isWriting: Boolean = false,
    val proposal: ProbeConfigProposal? = null,
    val message: ProbeConfigMessage? = null,
)

data class SettingsUiState(
    val settings: SensorSettings? = null,
    val isConnected: Boolean = false,
    val detection: DetectionState = DetectionState.Idle,
    val probe: ProbeConfigState = ProbeConfigState(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val sensorRepository: SoilSensorRepository,
) : ViewModel() {

    private val detection = MutableStateFlow<DetectionState>(DetectionState.Idle)
    private val probe = MutableStateFlow(ProbeConfigState())

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        sensorRepository.connectionState,
        detection,
        probe,
    ) { settings, connection, detectionState, probeState ->
        SettingsUiState(
            settings = settings,
            isConnected = connection is ConnectionState.Connected,
            detection = detectionState,
            probe = probeState,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch {
            sensorRepository.connectionState
                .map { it is ConnectionState.Connected }
                .distinctUntilChanged()
                .collect { connected -> if (connected) refreshProbeConfig() else probe.value = ProbeConfigState() }
        }
    }

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

    fun setUseWallpaperColors(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUseWallpaperColors(enabled) }
    }

    fun detectSlaveId() {
        if (detection.value == DetectionState.Running) return
        viewModelScope.launch {
            detection.value = DetectionState.Running
            detection.value = when (val result = sensorRepository.detectSlaveId()) {
                is SlaveIdDetection.Found -> DetectionState.Found(result.slaveId, result.baudRate)
                is SlaveIdDetection.Failed -> DetectionState.Failed(result.error)
            }
        }
    }

    /** Uses the detected slave ID and baud rate for the app, then reads the probe's settings with them. */
    fun applyDetectedSlaveId() {
        val found = detection.value as? DetectionState.Found ?: return
        detection.value = DetectionState.Idle
        viewModelScope.launch {
            settingsRepository.setSlaveId(found.slaveId)
            settingsRepository.setBaudRate(found.baudRate)
            refreshProbeConfig()
        }
    }

    fun refreshProbeConfig() {
        if (probe.value.isLoading || probe.value.isWriting) return
        probe.update { it.copy(isLoading = true, message = null) }
        viewModelScope.launch {
            val result = sensorRepository.readProbeConfig()
            probe.update {
                when (result) {
                    is SensorResult.Success -> it.copy(config = result.value, isLoading = false)
                    is SensorResult.Failure ->
                        it.copy(config = null, isLoading = false, message = ProbeConfigMessage.ReadFailed(result.error))
                }
            }
        }
    }

    /** Asks for confirmation; nothing is written until [confirmProbeChange]. */
    fun proposeProbeSlaveId(newSlaveId: Int) {
        val current = probe.value.config?.slaveId ?: return
        if (newSlaveId !in SensorSettings.SLAVE_ID_RANGE || newSlaveId == current) return
        probe.update { it.copy(proposal = ProbeConfigProposal.SlaveId(current, newSlaveId), message = null) }
    }

    fun proposeProbeBaudRate(newBaudRate: Int) {
        val current = probe.value.config?.baudRate ?: uiState.value.settings?.baudRate ?: return
        if (newBaudRate !in SoilSensorRegisters.SUPPORTED_BAUD_RATES || newBaudRate == current) return
        if (probe.value.config == null) return
        probe.update { it.copy(proposal = ProbeConfigProposal.BaudRate(current, newBaudRate), message = null) }
    }

    fun dismissProbeChange() {
        if (probe.value.isWriting) return
        probe.update { it.copy(proposal = null) }
    }

    fun confirmProbeChange() {
        val proposal = probe.value.proposal ?: return
        if (probe.value.isWriting) return
        probe.update { it.copy(isWriting = true) }
        viewModelScope.launch {
            val result = when (proposal) {
                is ProbeConfigProposal.SlaveId -> sensorRepository.changeProbeSlaveId(proposal.to)
                is ProbeConfigProposal.BaudRate -> sensorRepository.changeProbeBaudRate(proposal.to)
            }
            probe.update {
                when (result) {
                    is ProbeChange.Verified -> it.copy(config = result.config, message = ProbeConfigMessage.Verified)
                    ProbeChange.NeedsPowerCycle -> it.copy(config = null, message = ProbeConfigMessage.NeedsPowerCycle)
                    is ProbeChange.Failed -> it.copy(message = ProbeConfigMessage.WriteFailed(result.error))
                }.copy(isWriting = false, proposal = null)
            }
        }
    }
}
