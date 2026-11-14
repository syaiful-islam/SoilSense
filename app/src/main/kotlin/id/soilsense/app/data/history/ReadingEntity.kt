package id.soilsense.app.data.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import id.soilsense.app.data.model.RecordedReading
import id.soilsense.modbus.soil.SoilReading

@Entity(tableName = "readings", indices = [Index(value = ["timestamp_millis"])])
data class ReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "timestamp_millis") val timestampMillis: Long,
    // Added in schema version 2 (see SoilSenseDatabase.MIGRATION_1_2); the default must match the migration.
    @ColumnInfo(name = "name", defaultValue = "") val name: String = "",
    @ColumnInfo(name = "moisture_percent") val moisturePercent: Double,
    @ColumnInfo(name = "temperature_celsius") val temperatureCelsius: Double,
    @ColumnInfo(name = "conductivity_us_cm") val conductivityMicroSiemensPerCm: Int,
    @ColumnInfo(name = "ph") val ph: Double,
    @ColumnInfo(name = "nitrogen_mg_kg") val nitrogenMgPerKg: Int,
    @ColumnInfo(name = "phosphorus_mg_kg") val phosphorusMgPerKg: Int,
    @ColumnInfo(name = "potassium_mg_kg") val potassiumMgPerKg: Int,
    // Null when the probe's firmware does not expose salinity/TDS (registers 0x0007–0x0008).
    @ColumnInfo(name = "salinity_mg_l") val salinityMgPerL: Int? = null,
    @ColumnInfo(name = "tds_mg_l") val tdsMgPerL: Int? = null,
)

fun ReadingEntity.toModel(): RecordedReading = RecordedReading(
    id = id,
    timestampMillis = timestampMillis,
    name = name,
    reading = SoilReading(
        moisturePercent = moisturePercent,
        temperatureCelsius = temperatureCelsius,
        conductivityMicroSiemensPerCm = conductivityMicroSiemensPerCm,
        ph = ph,
        nitrogenMgPerKg = nitrogenMgPerKg,
        phosphorusMgPerKg = phosphorusMgPerKg,
        potassiumMgPerKg = potassiumMgPerKg,
        salinityMgPerL = salinityMgPerL,
        tdsMgPerL = tdsMgPerL,
    ),
)

fun SoilReading.toEntity(timestampMillis: Long, name: String = ""): ReadingEntity = ReadingEntity(
    timestampMillis = timestampMillis,
    name = name,
    moisturePercent = moisturePercent,
    temperatureCelsius = temperatureCelsius,
    conductivityMicroSiemensPerCm = conductivityMicroSiemensPerCm,
    ph = ph,
    nitrogenMgPerKg = nitrogenMgPerKg,
    phosphorusMgPerKg = phosphorusMgPerKg,
    potassiumMgPerKg = potassiumMgPerKg,
    salinityMgPerL = salinityMgPerL,
    tdsMgPerL = tdsMgPerL,
)
