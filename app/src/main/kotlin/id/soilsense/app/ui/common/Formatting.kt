package id.soilsense.app.ui.common

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import id.soilsense.app.R
import id.soilsense.app.data.model.SensorReadError
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Formats epoch millis with the user's locale and 12/24-hour preference ("skeleton" e.g. "jms"). */
@Composable
fun rememberTimeFormatter(skeleton: String): (Long) -> String {
    val locale = currentLocale()
    return remember(locale, skeleton) {
        val format = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        val formatter: (Long) -> String = { millis -> format.format(Date(millis)) }
        formatter
    }
}

@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** Title and troubleshooting hint for a failed poll. */
data class ReadErrorText(@param:StringRes val title: Int, @param:StringRes val hint: Int, val code: Int? = null)

fun SensorReadError.toText(): ReadErrorText = when (this) {
    SensorReadError.NoResponse -> ReadErrorText(R.string.error_no_response, R.string.error_no_response_hint)
    SensorReadError.CorruptedResponse -> ReadErrorText(R.string.error_corrupted, R.string.error_corrupted_hint)
    is SensorReadError.DeviceException ->
        ReadErrorText(R.string.error_device_exception, R.string.error_device_exception_hint, code)
    SensorReadError.ConnectionLost -> ReadErrorText(R.string.error_connection_lost, R.string.error_connection_lost_hint)
    SensorReadError.NotConnected -> ReadErrorText(R.string.error_not_connected, R.string.error_not_connected_hint)
}
