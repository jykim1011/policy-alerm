package com.policyalarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.policyalarm.ui.navigation.AppNavigation
import com.policyalarm.ui.theme.LocalThemeController
import com.policyalarm.ui.theme.PolicyAlarmTheme
import com.policyalarm.ui.theme.ThemeController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val policyId = intent.getStringExtra("policy_id")
        setContent {
            val systemDark = isSystemInDarkTheme()
            val themeController = remember { ThemeController(this, systemDark) }
            CompositionLocalProvider(LocalThemeController provides themeController) {
                PolicyAlarmTheme(darkTheme = themeController.isDark) {
                    AppNavigation(startPolicyId = policyId)
                }
            }
        }
    }
}
