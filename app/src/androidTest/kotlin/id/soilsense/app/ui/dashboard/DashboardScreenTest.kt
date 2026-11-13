package id.soilsense.app.ui.dashboard

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.soilsense.app.R
import id.soilsense.app.data.model.ConnectionState
import id.soilsense.app.data.model.UsbAdapterInfo
import id.soilsense.app.ui.theme.SoilSenseTheme
import id.soilsense.modbus.soil.SoilReading
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val adapter = UsbAdapterInfo("CH34x", 0x1A86, 0x7523)
    private val reading = SoilReading(42.2, 12.5, 1378, 7.9, 68, 96, 220)

    private fun show(state: DashboardUiState, onConnect: () -> Unit = {}, onSave: () -> Unit = {}, onData: () -> Unit = {}) {
        composeRule.setContent {
            SoilSenseTheme(dynamicColor = false) {
                DashboardScreen(
                    uiState = state,
                    snackbarHostState = SnackbarHostState(),
                    onConnect = onConnect,
                    onDisconnect = {},
                    onSaveReading = onSave,
                    onOpenData = onData,
                )
            }
        }
    }

    @Test
    fun connectButtonInvokesCallback() {
        var connectClicks = 0
        show(DashboardUiState(connection = ConnectionState.Disconnected(adapter)), onConnect = { connectClicks++ })

        composeRule.onNodeWithText(context.getString(R.string.action_connect)).performClick()

        assertEquals(1, connectClicks)
    }

    @Test
    fun showsAllSevenParametersAndSaves() {
        var saves = 0
        show(
            DashboardUiState(connection = ConnectionState.Connected(adapter, 4800), latestReading = reading),
            onSave = { saves++ },
        )

        listOf("12.5", "42.2", "1378", "7.9", "68", "96", "220").forEach {
            composeRule.onNodeWithText(it).assertExists()
        }
        composeRule.onNodeWithText(context.getString(R.string.action_save_reading)).performClick()
        assertEquals(1, saves)
    }

    @Test
    fun saveIsDisabledBeforeFirstReading() {
        show(DashboardUiState(connection = ConnectionState.Connected(adapter, 4800)))

        composeRule.onNodeWithText(context.getString(R.string.action_save_reading)).assertIsNotEnabled()
    }
}
