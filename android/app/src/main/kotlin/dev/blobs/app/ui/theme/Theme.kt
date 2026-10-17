package dev.blobs.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.blobs.app.data.preferences.ThemeMode

private val LightColors =
    lightColorScheme(
        primary = Blue,
        onPrimary = Color.White,
        primaryContainer = BlueSoft,
        onPrimaryContainer = Ink,
        background = Paper,
        onBackground = Ink,
        surface = Paper,
        onSurface = Ink,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF6F4EF),
        surfaceContainer = Color(0xFFF6F4EF),
        surfaceContainerHigh = BlueSoft,
        surfaceContainerHighest = Color(0xFFEDEBE6),
        surfaceVariant = Color(0xFFF6F4EF),
        onSurfaceVariant = Color(0xFF65656B),
        outline = Color(0xFF85848A),
        outlineVariant = Color(0xFFDEDDD8),
        secondary = Blue,
        onSecondary = Color.White,
        secondaryContainer = Lavender,
        onSecondaryContainer = Ink,
        tertiaryContainer = Color(0xFFC5E8FF),
        onTertiaryContainer = Ink,
        error = Color(0xFFB13636)
    )

private val DarkColors =
    darkColorScheme(
        primary = NightBlue,
        onPrimary = Night,
        primaryContainer = Color(0xFF313F59),
        onPrimaryContainer = NightInk,
        background = Night,
        onBackground = NightInk,
        surface = NightSurface,
        onSurface = NightInk,
        surfaceContainerLowest = NightSurface,
        surfaceContainerLow = Color(0xFF30343E),
        surfaceContainer = Color(0xFF30343E),
        surfaceContainerHigh = Color(0xFF3A3F4B),
        surfaceContainerHighest = Color(0xFF454A57),
        surfaceVariant = Color(0xFF30343E),
        onSurfaceVariant = Color(0xFFC1C3CD),
        outline = Color(0xFF9699A6),
        outlineVariant = Color(0xFF50535F),
        secondary = NightBlue,
        onSecondary = Night,
        secondaryContainer = Color(0xFF49405B),
        onSecondaryContainer = NightInk,
        tertiaryContainer = Color(0xFF304B5D),
        onTertiaryContainer = NightInk,
        error = Color(0xFFFFAAAA)
    )

@Composable
fun BlobsTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark =
        when (mode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = BlobsTypography,
        content = content
    )
}
