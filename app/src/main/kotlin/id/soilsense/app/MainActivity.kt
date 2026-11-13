package id.soilsense.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import id.soilsense.app.data.model.SensorSettings
import id.soilsense.app.data.settings.SettingsRepository
import id.soilsense.app.ui.SoilSenseApp
import id.soilsense.app.ui.theme.SoilSenseTheme
import javax.inject.Inject

/**
 * Single activity. It is also the target of USB_DEVICE_ATTACHED (see AndroidManifest.xml): when the
 * user opens the app from the "open with" prompt, Android grants USB permission for that adapter,
 * so connecting afterwards needs no second dialog.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = SensorSettings())
            SoilSenseTheme(dynamicColor = settings.useWallpaperColors) {
                SoilSenseApp()
            }
        }
    }
}
