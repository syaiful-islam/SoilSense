package id.soilsense.app.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.soilsense.app.data.model.ConnectionState
import id.soilsense.app.data.sensor.SoilSensorRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** USB adapter detection and connection, shown on the Device tab. */
@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val sensorRepository: SoilSensorRepository,
) : ViewModel() {

    // Already a hot StateFlow owned by the singleton repository, so it is exposed as is.
    val connection: StateFlow<ConnectionState> = sensorRepository.connectionState

    fun connect() {
        viewModelScope.launch { sensorRepository.connect() }
    }

    fun disconnect() {
        viewModelScope.launch { sensorRepository.disconnect() }
    }
}
