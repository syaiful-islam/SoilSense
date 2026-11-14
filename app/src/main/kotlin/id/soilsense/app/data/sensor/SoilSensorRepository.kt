package id.soilsense.app.data.sensor

import id.soilsense.app.data.model.ConnectionState
import id.soilsense.app.data.model.SensorReadError
import id.soilsense.app.data.model.SensorSample
import id.soilsense.modbus.soil.CalibrationSettings
import id.soilsense.modbus.soil.CalibrationWrite
import id.soilsense.modbus.soil.ProbeConfig
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

    /**
     * Finds the probe's slave ID and baud rate: asks through the 0xFF query address at the saved
     * baud rate first, then at the other supported ones. Only one probe may be on the bus. The port
     * is left at the saved baud rate; applying the result is up to the caller.
     */
    suspend fun detectSlaveId(): SlaveIdDetection

    /** Reads the slave ID and baud rate stored inside the probe. */
    suspend fun readProbeConfig(): SensorResult<ProbeConfig>

    /**
     * Stores a new slave ID in the probe, then checks which address it answers on. The app's own
     * slave ID follows the probe whenever the write may have taken effect.
     */
    suspend fun changeProbeSlaveId(newSlaveId: Int): ProbeChange

    /** Like [changeProbeSlaveId], for the baud rate (2400, 4800 or 9600). */
    suspend fun changeProbeBaudRate(newBaudRate: Int): ProbeChange

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
    data class Found(val slaveId: Int, val baudRate: Int) : SlaveIdDetection

    data class Failed(val error: SensorReadError) : SlaveIdDetection
}

/** Outcome of changing the probe's slave ID or baud rate. */
sealed interface ProbeChange {
    /** The probe answered with the new value; the app now uses it too. */
    data class Verified(val config: ProbeConfig) : ProbeChange

    /**
     * The value was sent and the app switched to it, but the probe does not answer with it yet.
     * Typically the probe applies it only after its power is cycled.
     */
    data object NeedsPowerCycle : ProbeChange

    /** Nothing changed: the probe rejected the write or still answers with the old value. */
    data class Failed(val error: SensorReadError) : ProbeChange
}
