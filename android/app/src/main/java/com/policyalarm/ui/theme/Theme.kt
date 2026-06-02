package com.policyalarm.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = LightAccent,
    onPrimary = Color.White,
    primaryContainer = LightGovTint,
    onPrimaryContainer = LightAccent,
    background = LightBgApp,
    onBackground = LightFgDefault,
    surface = LightBgSurface,
    onSurface = LightFgStrong,
    surfaceVariant = LightBgMuted,
    onSurfaceVariant = LightFgMuted,
    outline = LightBorderStrong,
    outlineVariant = LightBorder,
    error = Red500,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = DarkAccent,
    onPrimary = Color.White,
    primaryContainer = DarkGovTint,
    onPrimaryContainer = DarkAccent,
    background = DarkBgApp,
    onBackground = DarkFgDefault,
    surface = DarkBgSurface,
    onSurface = DarkFgStrong,
    surfaceVariant = DarkBgMuted,
    onSurfaceVariant = DarkFgMuted,
    outline = DarkBorderStrong,
    outlineVariant = DarkBorder,
    error = Red500,
    onError = Color.White,
)

@Composable
fun PolicyAlarmTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val appColors = if (darkTheme) DarkAppColors else LightAppColors
    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
