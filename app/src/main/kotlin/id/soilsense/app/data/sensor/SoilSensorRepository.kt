package id.soilsense.app.data.sensor

import id.soilsense.app.data.model.ConnectionState
import id.soilsense.app.data.model.SensorReadError
import id.soilsense.app.data.model.SensorSample
import id.soilsense.modbus.soil.CalibrationSettings
import id.soilsense.modbus.soil.CalibrationWrite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Single source of truth for the live probe connection. */
interface SoilSensorRepository {

    val connectionState: StateFlow<ConnectionState>

    /** Finds the adapter, asks for USB permission if needed and opens the port with the saved baud rate. */
    suspend fun connect()

    suspend fun disconnect()

    /**
     * Polls the probe at the configured interval while connected and while collected.
     * Collection stopping (e.g. the UI leaving the screen) stops polling, so nothing drains the
     * battery in the background.
     */
    fun liveReadings(): Flow<SensorSample>

    /** Reads the probe's slave ID through the 0xFF query address. Only one probe may be on the bus. */
    suspend fun detectSlaveId(): SlaveIdDetection

    /** One reading right now, independent of [liveReadings]; used as a calibration sample. */
    suspend fun readNow(): SensorSample

    /** Reads the calibration stored in the probe. */
    suspend fun readCalibration(): SensorResult<CalibrationSettings>

    /**
     * Writes [change] to the probe's memory (permanent until changed again) and returns the
     * calibration read back afterwards. Bus access is serialised with polling, so the two never collide.
     */
    suspend fun applyCalibration(change: CalibrationWrite): SensorResult<CalibrationSettings>
}

/** Outcome of a one-off request to the probe. */
sealed interface SensorResult<out T> {
    data class Success<T>(val value: T) : SensorResult<T>

    data class Failure(val error: SensorReadError) : SensorResult<Nothing>
}

sealed interface SlaveIdDetection {
    data class Found(val slaveId: Int) : SlaveIdDetection

    data class Failed(val error: SensorReadError) : SlaveIdDetection
}
