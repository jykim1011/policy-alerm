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

    // 아침 묶음 알림(morningDigest)은 특정 정책이 아니라 "open_tab=history"를 실어 보낸다.
    // 탭하면 앱의 알림 탭으로 이동시키기 위한 신호.
    private val openTab = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkPolicyId.value = policyIdFrom(intent)
        openTab.value = intent.getStringExtra("open_tab")
        setContent {
            val systemDark = isSystemInDarkTheme()
            val themeController = remember { ThemeController(this, systemDark) }
            val policyId by deepLinkPolicyId
            val tabRequest by openTab
            CompositionLocalProvider(LocalThemeController provides themeController) {
                PolicyAlarmTheme(darkTheme = themeController.isDark) {
                    AppNavigation(
                        deepLinkPolicyId = policyId,
                        // 소비 후 인텐트 extra도 지워 회전 등 Activity 재생성 시 재이동을 막는다.
                        onDeepLinkHandled = {
                            deepLinkPolicyId.value = null
                            intent.removeExtra("policy_id")
                            // 웹 링크(App Links)로 들어온 경우엔 extra가 아니라 data URI가
                            // 대상이므로 이것도 비워야 회전 등 재생성 시 재이동하지 않는다.
                            intent.data = null
                        },
                        openTab = tabRequest,
                        onOpenTabHandled = {
                            openTab.value = null
                            intent.removeExtra("open_tab")
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 앱이 실행 중일 때 알림을 탭한 경우. getIntent()가 새 인텐트를 가리키도록 교체하고
        // 딥링크 대상/탭 신호를 갱신해 상세 화면 또는 알림 탭으로 이동시킨다.
        setIntent(intent)
        deepLinkPolicyId.value = policyIdFrom(intent)
        openTab.value = intent.getStringExtra("open_tab")
    }

    /**
     * 딥링크 대상 정책 id를 뽑는다. 출처는 둘이다.
     *  - FCM 알림 탭: "policy_id" extra
     *  - 웹 공유 링크(App Links): https://policy-alerm.web.app/policy/{id}/ 의 마지막 경로 조각
     *
     * 웹 링크의 id는 한글이 섞여 퍼센트 인코딩돼 오므로 Uri.pathSegments로 디코딩된 값을 쓴다.
     */
    private fun policyIdFrom(intent: Intent): String? {
        intent.getStringExtra("policy_id")?.takeIf { it.isNotBlank() }?.let { return it }
        if (intent.action != Intent.ACTION_VIEW) return null
        val segments = intent.data?.pathSegments ?: return null
        if (segments.size < 2 || segments[0] != "policy") return null
        return segments[1].takeIf { it.isNotBlank() }
    }
}
