package id.soilsense.modbus.soil

/** One decoded measurement from the CWT-SOIL-NPKPHCTH-S probe (7 parameters, as printed on its label). */
public data class SoilReading(
    val moisturePercent: Double,
    val temperatureCelsius: Double,
    val conductivityMicroSiemensPerCm: Int,
    val ph: Double,
    val nitrogenMgPerKg: Int,
    val phosphorusMgPerKg: Int,
    val potassiumMgPerKg: Int,
)
