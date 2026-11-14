package id.soilsense.app.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import id.soilsense.app.R
import kotlinx.serialization.Serializable

@Serializable
data object DashboardDestination

@Serializable
data object HistoryDestination

@Serializable
data object DeviceDestination

@Serializable
data object CalibrationDestination

@Serializable
data object SettingsDestination

enum class TopLevelDestination(
    val route: Any,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    DASHBOARD(DashboardDestination, R.string.nav_dashboard, R.drawable.ic_dashboard),
    DEVICE(DeviceDestination, R.string.nav_device, R.drawable.ic_usb),
    CALIBRATION(CalibrationDestination, R.string.nav_calibration, R.drawable.ic_tune),
    HISTORY(HistoryDestination, R.string.nav_history, R.drawable.ic_history),
    SETTINGS(SettingsDestination, R.string.nav_settings, R.drawable.ic_settings),
}
