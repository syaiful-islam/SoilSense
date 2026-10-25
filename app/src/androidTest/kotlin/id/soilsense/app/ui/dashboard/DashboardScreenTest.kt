package id.soilsense.app.ui.dashboard

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.soilsense.app.R
import id.soilsense.app.data.model.ConnectionState
import id.soilsense.app.data.model.UsbAdapterInfo
import id.soilsense.app.ui.theme.SoilSenseTheme
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

    @Test
    fun connectButtonInvokesCallback() {
        var connectClicks = 0
        composeRule.setContent {
            SoilSenseTheme(dynamicColor = false) {
                DashboardScreen(
                    uiState = DashboardUiState(connection = ConnectionState.Disconnected(adapter)),
                    onConnect = { connectClicks++ },
                    onDisconnect = {},
                    onRecordingChange = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.action_connect)).performClick()

        assertEquals(1, connectClicks)
    }

    @Test
    fun recordingRowTogglesAsOneSwitch() {
        var recording: Boolean? = null
        composeRule.setContent {
            SoilSenseTheme(dynamicColor = false) {
                DashboardScreen(
                    uiState = DashboardUiState(connection = ConnectionState.Connected(adapter, 4800)),
                    onConnect = {},
                    onDisconnect = {},
                    onRecordingChange = { recording = it },
                )
            }
        }

        val row = composeRule.onNodeWithText(context.getString(R.string.recording_title))
        row.assertIsOff()
        row.performClick()

        assertEquals(true, recording)
    }
}
