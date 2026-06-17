package com.policyalarm

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.policyalarm.data.local.AppDatabase
import com.policyalarm.data.local.NotificationHistoryEntity
import com.policyalarm.ui.navigation.AppNavigation
import com.policyalarm.ui.theme.LocalThemeController
import com.policyalarm.ui.theme.PolicyAlarmTheme
import com.policyalarm.ui.theme.ThemeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val policyId = intent.getStringExtra("policy_id")
        // 앱이 백그라운드/종료 상태일 때 시스템이 알림을 표시하면 onMessageReceived가 호출되지 않음.
        // 사용자가 알림을 탭하면 FCM data 필드가 인텐트 extras로 전달되므로 여기서 DB에 저장.
        saveNotificationToDb(intent)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        saveNotificationToDb(intent)
    }

    private fun saveNotificationToDb(intent: Intent) {
        val policyId = intent.getStringExtra("policy_id") ?: return
        // body는 FCM data 맵에서만 오는 키. PolicyFcmService의 PendingIntent에는 없으므로
        // 이미 onMessageReceived에서 저장된 경우(body가 없는 탭)는 건너뜀.
        val body = intent.getStringExtra("body") ?: return
        val category = intent.getStringExtra("category") ?: ""
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDatabase.getInstance(applicationContext)
                    .notificationHistoryDao()
                    .insertIfAbsent(NotificationHistoryEntity(policyId, body, category))
            } catch (_: Exception) {}
        }
    }
}
