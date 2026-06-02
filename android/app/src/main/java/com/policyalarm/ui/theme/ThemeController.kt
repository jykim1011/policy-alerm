package com.policyalarm.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * App-wide light/dark mode state, persisted to SharedPreferences so the
 * choice survives restarts. The Settings screen toggles [isDark]; the theme
 * recomposes app-wide. Defaults to the system setting on first launch.
 */
class ThemeController(context: Context, systemDark: Boolean) {
    private val prefs = context.applicationContext
        .getSharedPreferences("app_theme", Context.MODE_PRIVATE)

    var isDark by mutableStateOf(prefs.getBoolean(KEY_DARK, systemDark))
        private set

    fun setDarkMode(dark: Boolean) {
        isDark = dark
        prefs.edit().putBoolean(KEY_DARK, dark).apply()
    }

    fun toggle() = setDarkMode(!isDark)

    private companion object {
        const val KEY_DARK = "is_dark"
    }
}

val LocalThemeController = staticCompositionLocalOf<ThemeController> {
    error("ThemeController not provided")
}
