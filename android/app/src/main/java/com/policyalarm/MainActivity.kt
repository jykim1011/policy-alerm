package com.policyalarm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.ContextCompat
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

    // Android 13(API 33)+ 는 POST_NOTIFICATIONS 가 런타임 권한이라, 매니페스트 선언만으론
    // 알림이 기본 차단된다. 결과는 OS가 처리하므로 콜백에서 별도 처리는 하지 않는다
    // (거부해도 앱은 동작; 설정에서 다시 켤 수 있다).
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensureNotificationPermission()
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

    // 첫 실행(및 아직 미허용 상태)일 때 알림 권한을 요청한다. 이미 허용됐거나 사용자가
    // 영구 거부한 경우 시스템이 다이얼로그를 띄우지 않으므로 매 실행 호출해도 안전하다.
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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
