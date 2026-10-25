package id.soilsense.app.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import id.soilsense.app.ui.dashboard.DashboardRoute
import id.soilsense.app.ui.history.HistoryRoute
import id.soilsense.app.ui.navigation.DashboardDestination
import id.soilsense.app.ui.navigation.HistoryDestination
import id.soilsense.app.ui.navigation.SettingsDestination
import id.soilsense.app.ui.navigation.TopLevelDestination
import id.soilsense.app.ui.settings.SettingsRoute
import kotlin.reflect.KClass

/**
 * App shell. NavigationSuiteScaffold switches between a bottom bar (phones), a navigation rail
 * (tablets, foldables, landscape) and a drawer based on the window size class.
 */
@Composable
fun SoilSenseApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                item(
                    selected = currentDestination.isInHierarchy(destination.route::class),
                    onClick = { navController.navigateToTopLevel(destination) },
                    icon = { Icon(painterResource(destination.iconRes), contentDescription = null) },
                    label = { Text(stringResource(destination.labelRes)) },
                )
            }
        },
    ) {
        NavHost(navController = navController, startDestination = DashboardDestination) {
            composable<DashboardDestination> { DashboardRoute() }
            composable<HistoryDestination> { HistoryRoute() }
            composable<SettingsDestination> { SettingsRoute() }
        }
    }
}

private fun NavDestination?.isInHierarchy(route: KClass<*>): Boolean =
    this?.hierarchy?.any { it.hasRoute(route) } ?: false

private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        // Single back stack per tab: back from any tab returns to the dashboard, then exits.
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
