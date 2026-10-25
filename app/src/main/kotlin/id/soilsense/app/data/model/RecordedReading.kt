package id.soilsense.app.data.model

import id.soilsense.modbus.soil.SoilReading

data class RecordedReading(
    val id: Long,
    val timestampMillis: Long,
    val reading: SoilReading,
)
