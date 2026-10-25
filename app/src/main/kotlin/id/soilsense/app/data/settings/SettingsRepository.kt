package id.soilsense.app.data.settings

import id.soilsense.app.data.model.SensorSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<SensorSettings>

    suspend fun setSlaveId(slaveId: Int)

    suspend fun setBaudRate(baudRate: Int)

    suspend fun setPollIntervalSeconds(seconds: Int)

    suspend fun setRecordingEnabled(enabled: Boolean)
}
