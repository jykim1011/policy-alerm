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
import com.policyalarm.ui.navigation.AppNavigation
import com.policyalarm.ui.theme.LocalThemeController
import com.policyalarm.ui.theme.PolicyAlarmTheme
import com.policyalarm.ui.theme.ThemeController

class MainActivity : ComponentActivity() {
    // 알림 탭으로 들어온 policy_id. onCreate(콜드 스타트)·onNewIntent(앱 실행 중) 양쪽에서
    // 갱신되며, AppNavigation이 이를 관찰해 상세 화면으로 이동한다.
    // (알림 기록 자체는 Cloud Function이 Firestore에 써 두므로 여기서 저장하지 않는다.)
    private val deepLinkPolicyId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkPolicyId.value = intent.getStringExtra("policy_id")
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
        deepLinkPolicyId.value = intent.getStringExtra("policy_id")
    }
}
