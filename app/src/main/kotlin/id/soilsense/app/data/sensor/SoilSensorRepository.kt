package id.soilsense.app.data.sensor

import id.soilsense.app.data.model.ConnectionState
import id.soilsense.app.data.model.SensorReadError
import id.soilsense.app.data.model.SensorSample
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
}

sealed interface SlaveIdDetection {
    data class Found(val slaveId: Int) : SlaveIdDetection

    data class Failed(val error: SensorReadError) : SlaveIdDetection
}
