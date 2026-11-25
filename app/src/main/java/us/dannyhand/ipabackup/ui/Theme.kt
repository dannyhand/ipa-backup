package us.dannyhand.ipabackup.ui

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF001C3A),
    secondaryContainer = Color(0xFFDAE2F9),
    tertiaryContainer = Color(0xFFFFDDB3),
    onTertiaryContainer = Color(0xFF2A1700),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA4C8FF),
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF00468C),
    onPrimaryContainer = Color(0xFFD3E4FF),
    secondaryContainer = Color(0xFF3E4759),
    tertiaryContainer = Color(0xFF5C4000),
    onTertiaryContainer = Color(0xFFFFDDB3),
)

/** Material You: wallpaper-based colors on Android 12+, the app's blue elsewhere; follows dark mode. */
@Composable
fun IpaBackupTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= 31 -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
