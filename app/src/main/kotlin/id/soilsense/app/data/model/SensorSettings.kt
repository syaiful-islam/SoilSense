package id.soilsense.app.data.model

import id.soilsense.modbus.soil.SoilSensorRegisters

data class SensorSettings(
    val slaveId: Int = SoilSensorRegisters.DEFAULT_SLAVE_ID,
    val baudRate: Int = SoilSensorRegisters.DEFAULT_BAUD_RATE,
    val pollIntervalSeconds: Int = DEFAULT_POLL_INTERVAL_SECONDS,
    /** Material You colours from the wallpaper (Android 12+) instead of the app's blue-teal palette. */
    val useWallpaperColors: Boolean = false,
) {
    companion object {
        /** Range the probe accepts in register 0x07D0 (manual V1.4: 1–254). */
        val SLAVE_ID_RANGE: IntRange = 1..254
        val POLL_INTERVAL_RANGE_SECONDS: IntRange = 1..60

        // pH needs up to 10 s to settle (datasheet), so sub-second polling adds bus load without information.
        const val DEFAULT_POLL_INTERVAL_SECONDS: Int = 2
    }
}
