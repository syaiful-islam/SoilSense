package id.soilsense.app.screenshots

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.soilsense.app.data.model.ConnectionState
import id.soilsense.app.data.model.RecordedReading
import id.soilsense.app.data.model.SensorReadError
import id.soilsense.app.data.model.SensorSettings
import id.soilsense.app.data.model.UsbAdapterInfo
import id.soilsense.app.ui.dashboard.DashboardScreen
import id.soilsense.app.ui.dashboard.DashboardUiState
import id.soilsense.app.ui.device.DeviceScreen
import id.soilsense.app.ui.history.HistoryScreen
import id.soilsense.app.ui.history.HistoryUiState
import id.soilsense.app.ui.navigation.TopLevelDestination
import id.soilsense.app.ui.settings.SettingsScreen
import id.soilsense.app.ui.settings.SettingsUiState
import id.soilsense.app.ui.theme.SoilSenseTheme
import id.soilsense.modbus.soil.SoilReading
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Renders each screen with sample data and saves a PNG per screen into the app's files/screenshots
 * directory, so CI can pull them off the emulator for visual review. Not a pass/fail comparison.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotCaptureTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val adapter = UsbAdapterInfo("CH34x", 0x1A86, 0x7523)
    private val reading = SoilReading(42.2, 12.5, 1378, 7.9, 68, 96, 220, salinityMgPerL = 758, tdsMgPerL = 689)
    private val now = 1_763_080_000_000L

    @Test
    fun homeConnectedLight() = capture("1_home_light", TopLevelDestination.DASHBOARD) {
        DashboardScreen(
            uiState = DashboardUiState(
                connection = ConnectionState.Connected(adapter, 4800),
                latestReading = reading,
                lastUpdatedMillis = now,
                pollIntervalSeconds = 2,
            ),
            snackbarHostState = SnackbarHostState(),
            onSaveReading = {},
            onOpenDevice = {},
        )
    }

    @Test
    fun homeConnectedDark() = capture("2_home_dark", TopLevelDestination.DASHBOARD, dark = true) {
        DashboardScreen(
            uiState = DashboardUiState(
                connection = ConnectionState.Connected(adapter, 4800),
                latestReading = reading.copy(ph = 6.1),
                lastUpdatedMillis = now,
                lastError = SensorReadError.NoResponse,
                pollIntervalSeconds = 2,
            ),
            snackbarHostState = SnackbarHostState(),
            onSaveReading = {},
            onOpenDevice = {},
        )
    }

    @Test
    fun homeNotConnected() = capture("3_home_not_connected", TopLevelDestination.DASHBOARD) {
        DashboardScreen(
            uiState = DashboardUiState(connection = ConnectionState.NoDevice),
            snackbarHostState = SnackbarHostState(),
            onSaveReading = {},
            onOpenDevice = {},
        )
    }

    @Test
    fun device() = capture("4_device", TopLevelDestination.DEVICE) {
        DeviceScreen(connection = ConnectionState.Disconnected(adapter), onConnect = {}, onDisconnect = {})
    }

    @Test
    fun deviceDark() = capture("5_device_dark", TopLevelDestination.DEVICE, dark = true) {
        DeviceScreen(connection = ConnectionState.Connected(adapter, 4800), onConnect = {}, onDisconnect = {})
    }

    @Test
    fun history() = capture("6_history", TopLevelDestination.HISTORY) {
        HistoryScreen(
            uiState = HistoryUiState.Loaded(
                items = listOf(
                    RecordedReading(3, now, reading, "Lahan A, baris 3"),
                    RecordedReading(2, now - 3_600_000, reading.copy(ph = 6.2, nitrogenMgPerKg = 41), "Kebun cabai"),
                    RecordedReading(1, now - 86_400_000, reading.copy(moisturePercent = 18.4), ""),
                ),
                totalCount = 3,
                isExporting = false,
            ),
            snackbarHostState = SnackbarHostState(),
            onExport = {},
            onClear = {},
        )
    }

    @Test
    fun settings() = capture("7_settings", TopLevelDestination.SETTINGS) {
        SettingsScreen(
            uiState = SettingsUiState(settings = SensorSettings(), isConnected = true),
            onSlaveIdChange = {},
            onBaudRateChange = {},
            onPollIntervalChange = {},
            onWallpaperColorsChange = {},
            onDetectSlaveId = {},
            onApplyDetectedSlaveId = {},
        )
    }

    private fun capture(
        name: String,
        selected: TopLevelDestination,
        dark: Boolean = false,
        screen: @Composable () -> Unit,
    ) {
        composeRule.runOnUiThread { composeRule.activity.enableEdgeToEdge() }
        composeRule.setContent {
            SoilSenseTheme(darkTheme = dark, dynamicColor = false) {
                // Same shell as the real app, so the bottom navigation is part of the picture.
                NavigationSuiteScaffold(
                    navigationSuiteItems = {
                        TopLevelDestination.entries.forEach { destination ->
                            item(
                                selected = destination == selected,
                                onClick = {},
                                icon = { Icon(painterResource(destination.iconRes), contentDescription = null) },
                                label = { Text(stringResource(destination.labelRes)) },
                            )
                        }
                    },
                ) { screen() }
            }
        }
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "screenshots")
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
