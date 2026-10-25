package id.soilsense.modbus.soil

/**
 * One decoded measurement from the CWT-SOIL-NPKPHCTH-S (7-in-1) probe.
 *
 * [salinityMgPerL] and [tdsMgPerL] are null when the firmware only exposes registers 0x0000–0x0006.
 */
public data class SoilReading(
    val moisturePercent: Double,
    val temperatureCelsius: Double,
    val conductivityMicroSiemensPerCm: Int,
    val ph: Double,
    val nitrogenMgPerKg: Int,
    val phosphorusMgPerKg: Int,
    val potassiumMgPerKg: Int,
    val salinityMgPerL: Int?,
    val tdsMgPerL: Int?,
)
