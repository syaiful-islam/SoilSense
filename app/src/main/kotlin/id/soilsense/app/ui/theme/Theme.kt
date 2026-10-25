package id.soilsense.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Fallback palette (pre-Android 12) generated around a leaf-green seed; Material tonal pairs keep
// text/container combinations at WCAG AA contrast.
private val LightColors = lightColorScheme(
    primary = Color(0xFF2E6B30),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB0F2A8),
    onPrimaryContainer = Color(0xFF002204),
    secondary = Color(0xFF6B5D3F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF4E0BB),
    onSecondaryContainer = Color(0xFF241A04),
    tertiary = Color(0xFF38656A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCEBF0),
    onTertiaryContainer = Color(0xFF002023),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7FBF1),
    onBackground = Color(0xFF181D17),
    surface = Color(0xFFF7FBF1),
    onSurface = Color(0xFF181D17),
    surfaceVariant = Color(0xFFDEE5D8),
    onSurfaceVariant = Color(0xFF424940),
    outline = Color(0xFF72796F),
    outlineVariant = Color(0xFFC2C9BD),
    surfaceDim = Color(0xFFD7DBD2),
    surfaceBright = Color(0xFFF7FBF1),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F5EC),
    surfaceContainer = Color(0xFFEBEFE6),
    surfaceContainerHigh = Color(0xFFE6E9E0),
    surfaceContainerHighest = Color(0xFFE0E4DA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF95D58E),
    onPrimary = Color(0xFF00390A),
    primaryContainer = Color(0xFF14521B),
    onPrimaryContainer = Color(0xFFB0F2A8),
    secondary = Color(0xFFD7C4A0),
    onSecondary = Color(0xFF3A2F15),
    secondaryContainer = Color(0xFF52452A),
    onSecondaryContainer = Color(0xFFF4E0BB),
    tertiary = Color(0xFFA0CFD4),
    onTertiary = Color(0xFF00363B),
    tertiaryContainer = Color(0xFF1E4D52),
    onTertiaryContainer = Color(0xFFBCEBF0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF10140F),
    onBackground = Color(0xFFE0E4DA),
    surface = Color(0xFF10140F),
    onSurface = Color(0xFFE0E4DA),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC2C9BD),
    outline = Color(0xFF8C9388),
    outlineVariant = Color(0xFF424940),
    surfaceDim = Color(0xFF10140F),
    surfaceBright = Color(0xFF363A34),
    surfaceContainerLowest = Color(0xFF0B0F0A),
    surfaceContainerLow = Color(0xFF181D17),
    surfaceContainer = Color(0xFF1C211B),
    surfaceContainerHigh = Color(0xFF272B25),
    surfaceContainerHighest = Color(0xFF323630),
)

@Composable
fun SoilSenseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
