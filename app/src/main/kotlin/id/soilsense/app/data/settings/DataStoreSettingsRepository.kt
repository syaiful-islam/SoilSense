package id.soilsense.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import id.soilsense.app.data.model.SensorSettings
import id.soilsense.modbus.soil.SoilSensorRegisters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<SensorSettings> = dataStore.data
        .catch { error ->
            // A corrupt or unreadable file must not take the app down; fall back to defaults.
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs -> prefs.toSettings() }
        .distinctUntilChanged()

    override suspend fun setSlaveId(slaveId: Int) {
        require(slaveId in SensorSettings.SLAVE_ID_RANGE) { "Slave ID $slaveId out of range" }
        dataStore.edit { it[Keys.SLAVE_ID] = slaveId }
    }

    override suspend fun setBaudRate(baudRate: Int) {
        require(baudRate in SoilSensorRegisters.SUPPORTED_BAUD_RATES) { "Unsupported baud rate $baudRate" }
        dataStore.edit { it[Keys.BAUD_RATE] = baudRate }
    }

    override suspend fun setPollIntervalSeconds(seconds: Int) {
        require(seconds in SensorSettings.POLL_INTERVAL_RANGE_SECONDS) { "Interval $seconds s out of range" }
        dataStore.edit { it[Keys.POLL_INTERVAL_SECONDS] = seconds }
    }

    override suspend fun setRecordingEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.RECORDING_ENABLED] = enabled }
    }

    private fun Preferences.toSettings(): SensorSettings {
        val defaults = SensorSettings()
        return SensorSettings(
            slaveId = this[Keys.SLAVE_ID]?.takeIf { it in SensorSettings.SLAVE_ID_RANGE } ?: defaults.slaveId,
            baudRate = this[Keys.BAUD_RATE]?.takeIf { it in SoilSensorRegisters.SUPPORTED_BAUD_RATES }
                ?: defaults.baudRate,
            pollIntervalSeconds = this[Keys.POLL_INTERVAL_SECONDS]
                ?.takeIf { it in SensorSettings.POLL_INTERVAL_RANGE_SECONDS }
                ?: defaults.pollIntervalSeconds,
            recordingEnabled = this[Keys.RECORDING_ENABLED] ?: defaults.recordingEnabled,
        )
    }

    private object Keys {
        val SLAVE_ID = intPreferencesKey("slave_id")
        val BAUD_RATE = intPreferencesKey("baud_rate")
        val POLL_INTERVAL_SECONDS = intPreferencesKey("poll_interval_seconds")
        val RECORDING_ENABLED = booleanPreferencesKey("recording_enabled")
    }
}
