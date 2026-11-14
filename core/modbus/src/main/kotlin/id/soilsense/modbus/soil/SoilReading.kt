package id.soilsense.modbus.soil

/**
 * One decoded measurement from the CWT-SOIL-NPKPHCTH-S probe: the 7 measured parameters printed on
 * its label, plus salinity and TDS.
 *
 * Salinity and TDS are not measured separately: the probe derives them from EC with its salinity
 * (default 0.55) and TDS (default 0.50) factors. They are null when the firmware does not expose
 * registers 0x0007–0x0008.
 */
public data class SoilReading(
    val moisturePercent: Double,
    val temperatureCelsius: Double,
    val conductivityMicroSiemensPerCm: Int,
    val ph: Double,
    val nitrogenMgPerKg: Int,
    val phosphorusMgPerKg: Int,
    val potassiumMgPerKg: Int,
    val salinityMgPerL: Int? = null,
    val tdsMgPerL: Int? = null,
)
