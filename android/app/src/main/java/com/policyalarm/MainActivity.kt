package com.policyalarm

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
    // 알림 탭으로 들어온 policy_id. onCreate(콜드 스타트)·onNewIntent(앱 실행 중) 양쪽에서
    // 갱신되며, AppNavigation이 이를 관찰해 상세 화면으로 이동한다.
    private val deepLinkPolicyId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkPolicyId.value = intent.getStringExtra("policy_id")
        // 앱이 백그라운드/종료 상태일 때 시스템이 알림을 표시하면 onMessageReceived가 호출되지 않음.
        // 사용자가 알림을 탭하면 FCM data 필드가 인텐트 extras로 전달되므로 여기서 DB에 저장.
        saveNotificationToDb(intent)
        setContent {
            val systemDark = isSystemInDarkTheme()
            val themeController = remember { ThemeController(this, systemDark) }
            val policyId by deepLinkPolicyId
            CompositionLocalProvider(LocalThemeController provides themeController) {
                PolicyAlarmTheme(darkTheme = themeController.isDark) {
                    AppNavigation(
                        deepLinkPolicyId = policyId,
                        // 소비 후 인텐트 extra도 지워 회전 등 Activity 재생성 시 재이동을 막는다.
                        onDeepLinkHandled = {
                            deepLinkPolicyId.value = null
                            intent.removeExtra("policy_id")
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 앱이 실행 중일 때 알림을 탭한 경우. getIntent()가 새 인텐트를 가리키도록 교체하고
        // 딥링크 대상을 갱신해 상세 화면으로 이동시킨다.
        setIntent(intent)
        saveNotificationToDb(intent)
        deepLinkPolicyId.value = intent.getStringExtra("policy_id")
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
