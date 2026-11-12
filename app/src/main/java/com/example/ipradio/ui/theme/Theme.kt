package com.example.ipradio.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB5F1C9),
    onPrimary = Color(0xFF0F3323),
    primaryContainer = Color(0xFF223C2F),
    onPrimaryContainer = Color(0xFFF2F8F2),
    background = Color(0xFF121815),
    onBackground = Color(0xFFF2F8F2),
    surface = Color(0xFF1B2420),
    onSurface = Color(0xFFF2F8F2),
    surfaceVariant = Color(0xFF2D3A32),
    onSurfaceVariant = Color(0xFFADC0B2),
    outlineVariant = Color(0xFF34443A)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF176144),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEFE4),
    onPrimaryContainer = Color(0xFF17251E),
    background = Color(0xFFF7F9F7),
    onBackground = Color(0xFF17251E),
    surface = Color.White,
    onSurface = Color(0xFF17251E),
    surfaceVariant = Color(0xFFE4EDE6),
    onSurfaceVariant = Color(0xFF52645A),
    outlineVariant = Color(0xFFDCE6DE)
)

@Composable
fun IPRadioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
