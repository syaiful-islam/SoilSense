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
data object SettingsDestination

enum class TopLevelDestination(
    val route: Any,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    DASHBOARD(DashboardDestination, R.string.nav_dashboard, R.drawable.ic_dashboard),
    HISTORY(HistoryDestination, R.string.nav_history, R.drawable.ic_history),
    SETTINGS(SettingsDestination, R.string.nav_settings, R.drawable.ic_settings),
}
