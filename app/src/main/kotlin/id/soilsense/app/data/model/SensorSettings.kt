package id.soilsense.app.data.model

import id.soilsense.modbus.soil.SoilSensorRegisters

data class SensorSettings(
    val slaveId: Int = SoilSensorRegisters.DEFAULT_SLAVE_ID,
    val baudRate: Int = SoilSensorRegisters.DEFAULT_BAUD_RATE,
    val pollIntervalSeconds: Int = DEFAULT_POLL_INTERVAL_SECONDS,
    val recordingEnabled: Boolean = false,
) {
    companion object {
        /** Standard Modbus unicast range; 0 is broadcast and 248–255 are reserved. */
        val SLAVE_ID_RANGE: IntRange = 1..247
        val POLL_INTERVAL_RANGE_SECONDS: IntRange = 1..60

        // pH needs up to 10 s to settle (datasheet), so sub-second polling adds bus load without information.
        const val DEFAULT_POLL_INTERVAL_SECONDS: Int = 2
    }
}
