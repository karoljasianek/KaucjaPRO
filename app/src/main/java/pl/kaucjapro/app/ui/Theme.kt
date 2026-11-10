package pl.kaucjapro.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B7F4B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBFEBD2),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFFB7791F),
    onSecondary = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD9A5),
    onPrimary = Color(0xFF00391B),
    primaryContainer = Color(0xFF00522A),
    onPrimaryContainer = Color(0xFFBFEBD2),
    secondary = Color(0xFFFFC107),
    onSecondary = Color(0xFF3B2A00)
)

@Composable
fun KaucjaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
