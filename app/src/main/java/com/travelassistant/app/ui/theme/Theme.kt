package com.travelassistant.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val TravelColorScheme = darkColorScheme(
    primary = Up,
    onPrimary = Background,
    secondary = Accent,
    onSecondary = Background,
    tertiary = Info,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    error = Down,
    onError = Background,
    outline = Divider,
)

@Composable
fun TravelAssistantTheme(
    // The app is designed dark-first, mirroring trading terminals.
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge-to-edge draws behind the bars; keep their icons light on our dark UI.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = TravelColorScheme,
        typography = TravelTypography,
        content = content,
    )
}
