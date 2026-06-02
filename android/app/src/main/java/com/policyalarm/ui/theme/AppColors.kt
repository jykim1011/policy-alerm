package com.policyalarm.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Extended semantic color roles from the design system that don't map cleanly
 * onto Material3's ColorScheme (5 foreground levels, two borders, surface-2,
 * government tint, etc). Read these in screens via [LocalAppColors].
 */
@Immutable
data class AppColors(
    val bgApp: Color,
    val bgSurface: Color,
    val bgSurface2: Color,
    val bgMuted: Color,
    val fgStrong: Color,
    val fgDefault: Color,
    val fgMuted: Color,
    val fgSubtle: Color,
    val fgFaint: Color,
    val fgInverse: Color,
    val border: Color,
    val borderStrong: Color,
    val accent: Color,
    val accentHover: Color,
    val govTint: Color,
    val danger: Color,
    val isDark: Boolean,
)

val LightAppColors = AppColors(
    bgApp = LightBgApp,
    bgSurface = LightBgSurface,
    bgSurface2 = LightBgSurface2,
    bgMuted = LightBgMuted,
    fgStrong = LightFgStrong,
    fgDefault = LightFgDefault,
    fgMuted = LightFgMuted,
    fgSubtle = LightFgSubtle,
    fgFaint = LightFgFaint,
    fgInverse = Color.White,
    border = LightBorder,
    borderStrong = LightBorderStrong,
    accent = LightAccent,
    accentHover = LightAccentHover,
    govTint = LightGovTint,
    danger = Red500,
    isDark = false,
)

val DarkAppColors = AppColors(
    bgApp = DarkBgApp,
    bgSurface = DarkBgSurface,
    bgSurface2 = DarkBgSurface2,
    bgMuted = DarkBgMuted,
    fgStrong = DarkFgStrong,
    fgDefault = DarkFgDefault,
    fgMuted = DarkFgMuted,
    fgSubtle = DarkFgSubtle,
    fgFaint = DarkFgFaint,
    fgInverse = Gray900,
    border = DarkBorder,
    borderStrong = DarkBorderStrong,
    accent = DarkAccent,
    accentHover = DarkAccentHover,
    govTint = DarkGovTint,
    danger = Red500,
    isDark = true,
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }
