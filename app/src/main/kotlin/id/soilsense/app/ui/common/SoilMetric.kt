package id.soilsense.app.ui.common

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import id.soilsense.app.R
import id.soilsense.modbus.soil.SoilReading

/**
 * The 7 parameters printed on the probe's label, in display order, with the colour that identifies
 * each one on every screen (tiles on Home, chips in History).
 *
 * Both gradient stops keep white text at WCAG AA contrast (≥ 4.5:1), so text may sit anywhere on a tile.
 */
enum class SoilMetric(
    @param:StringRes val label: Int,
    @param:StringRes val shortLabel: Int,
    @param:StringRes val unit: Int?,
    val light: Color,
    val dark: Color,
    val icon: MetricIcon,
) {
    TEMPERATURE(
        R.string.metric_temperature,
        R.string.metric_short_temperature,
        R.string.unit_celsius,
        Color(0xFF2E7D32), // 5.1:1
        Color(0xFF1B5E20),
        MetricIcon.Drawable(R.drawable.ic_thermostat),
    ),
    MOISTURE(
        R.string.metric_moisture,
        R.string.metric_short_moisture,
        R.string.unit_percent,
        Color(0xFF1565C0), // 5.8:1
        Color(0xFF0D47A1),
        MetricIcon.Drawable(R.drawable.ic_water_drop),
    ),
    CONDUCTIVITY(
        R.string.metric_conductivity,
        R.string.metric_short_conductivity,
        R.string.unit_us_cm,
        Color(0xFF8E24AA), // 7.0:1
        Color(0xFF6A1B9A),
        MetricIcon.Drawable(R.drawable.ic_bolt),
    ),
    PH(
        R.string.metric_ph,
        R.string.metric_ph,
        null,
        Color(0xFFBF360C), // 5.6:1
        Color(0xFF8D2A0B),
        MetricIcon.Drawable(R.drawable.ic_science),
    ),
    NITROGEN(
        R.string.metric_nitrogen,
        R.string.symbol_nitrogen,
        R.string.unit_mg_kg,
        Color(0xFFC2185B), // 5.9:1
        Color(0xFF880E4F),
        MetricIcon.Element(R.string.symbol_nitrogen),
    ),
    PHOSPHORUS(
        R.string.metric_phosphorus,
        R.string.symbol_phosphorus,
        R.string.unit_mg_kg,
        Color(0xFF0277BD), // 4.8:1
        Color(0xFF01579B),
        MetricIcon.Element(R.string.symbol_phosphorus),
    ),
    POTASSIUM(
        R.string.metric_potassium,
        R.string.symbol_potassium,
        R.string.unit_mg_kg,
        Color(0xFF00838F), // 4.5:1
        Color(0xFF006064),
        MetricIcon.Element(R.string.symbol_potassium),
    ),
    ;

    val gradient: Brush get() = Brush.linearGradient(listOf(light, dark))

    /** Double values are shown with one decimal, Int values as is. */
    fun valueOf(reading: SoilReading): Number = when (this) {
        TEMPERATURE -> reading.temperatureCelsius
        MOISTURE -> reading.moisturePercent
        CONDUCTIVITY -> reading.conductivityMicroSiemensPerCm
        PH -> reading.ph
        NITROGEN -> reading.nitrogenMgPerKg
        PHOSPHORUS -> reading.phosphorusMgPerKg
        POTASSIUM -> reading.potassiumMgPerKg
    }
}

sealed interface MetricIcon {
    data class Drawable(@param:DrawableRes val res: Int) : MetricIcon

    /** N, P and K have no good pictogram; their element symbol is the convention on soil meters. */
    data class Element(@param:StringRes val symbol: Int) : MetricIcon
}

@Composable
fun formatMetricValue(value: Number): String = when (value) {
    is Double -> stringResource(R.string.value_one_decimal, value)
    else -> stringResource(R.string.value_integer, value.toInt())
}

/** Soil reaction class used by agronomists: below 6.5 acidic, 6.5–7.5 neutral, above 7.5 alkaline. */
enum class PhClass(@param:StringRes val label: Int) {
    ACIDIC(R.string.ph_acidic),
    NEUTRAL(R.string.ph_neutral),
    ALKALINE(R.string.ph_alkaline),
    ;

    companion object {
        fun of(ph: Double): PhClass = when {
            ph < 6.5 -> ACIDIC
            ph <= 7.5 -> NEUTRAL
            else -> ALKALINE
        }
    }
}

/** Brand gradient of the Home header (blue → teal); white text ≥ 4.5:1 on both stops. */
val HeaderGradient: Brush = Brush.linearGradient(listOf(Color(0xFF1565C0), Color(0xFF00796B)))
val HeaderBlue: Color = Color(0xFF1565C0)
