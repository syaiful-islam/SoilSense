package id.soilsense.app.data.history

import id.soilsense.app.data.model.RecordedReading
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Formats readings as RFC 4180 CSV.
 *
 * Numbers always use '.' as decimal separator (Locale.ROOT) so spreadsheets in any locale can
 * parse the file; timestamps are ISO-8601 with offset to stay unambiguous across time zones.
 */
class CsvFormatter(private val timeZone: TimeZone = TimeZone.getDefault()) {

    fun header(): String = HEADER

    fun row(item: RecordedReading): String {
        val r = item.reading
        return listOf(
            formatTimestamp(item.timestampMillis),
            "%.1f".format(Locale.ROOT, r.moisturePercent),
            "%.1f".format(Locale.ROOT, r.temperatureCelsius),
            r.conductivityMicroSiemensPerCm.toString(),
            "%.1f".format(Locale.ROOT, r.ph),
            r.nitrogenMgPerKg.toString(),
            r.phosphorusMgPerKg.toString(),
            r.potassiumMgPerKg.toString(),
            r.salinityMgPerL?.toString().orEmpty(),
            r.tdsMgPerL?.toString().orEmpty(),
        ).joinToString(",")
    }

    private fun formatTimestamp(millis: Long): String =
        // SimpleDateFormat is not thread-safe, so create one per call; export is not a hot path.
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT)
            .apply { timeZone = this@CsvFormatter.timeZone }
            .format(Date(millis))

    companion object {
        const val HEADER: String =
            "timestamp,moisture_percent,temperature_celsius,conductivity_us_cm,ph," +
                "nitrogen_mg_kg,phosphorus_mg_kg,potassium_mg_kg,salinity_mg_l,tds_mg_l"
    }
}
