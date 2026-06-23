package com.policyalarm.ui.screens.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.google.firebase.messaging.FirebaseMessaging
import com.policyalarm.data.repository.NotificationRepository
import com.policyalarm.data.repository.UserRepository
import com.policyalarm.ui.screens.history.HistoryScreen
import com.policyalarm.ui.screens.home.HomeScreen
import com.policyalarm.ui.screens.home.HomeViewModel
import com.policyalarm.ui.screens.home.HomeViewModelFactory
import com.policyalarm.ui.screens.settings.SettingsScreen
import com.policyalarm.ui.theme.LocalAppColors
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.policyalarm.ui.components.AdBanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private enum class Tab { HOME, HISTORY, SETTINGS }

@Composable
fun MainScaffold(
    onPolicyClick: (String) -> Unit,
    onArchiveClick: () -> Unit = {},
    onLogout: () -> Unit,
    onLicensesClick: () -> Unit = {},
) {
    val c = LocalAppColors.current
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }

    // 홈이 아닌 탭에서 뒤로가기 → 홈으로. 홈에서 뒤로가기 → 시스템 기본(앱 종료)
    BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }

    // Application.onCreate의 syncFcmTokenOnAuthReady가 실패한 경우를 대비한 폴백.
    // MainScaffold는 로그인 완료 후에만 진입하므로 여기서는 uid가 항상 유효하다.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                UserRepository().updateFcmToken(token)
            }
        }
    }

    // 알림 권한은 콜드 스타트가 아니라 로그인/온보딩을 마치고 메인에 진입한 이 시점에 요청한다
    // (사용자가 앱 가치를 본 뒤 물어 수락률을 높인다). 메인 진입 시 1회만 요청.
    RequestNotificationPermissionOnce()

    val context = LocalContext.current
    val homeVm = viewModel<HomeViewModel>(factory = HomeViewModelFactory(context))
    val notifRepo = remember { NotificationRepository() }
    val unreadCount by notifRepo.observeUnreadCount()
        .collectAsStateWithLifecycle(initialValue = 0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bgApp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                Tab.HOME -> HomeScreen(onPolicyClick = onPolicyClick, onArchiveClick = onArchiveClick, vm = homeVm)
                Tab.HISTORY -> HistoryScreen(onPolicyClick = onPolicyClick)
                Tab.SETTINGS -> SettingsScreen(
                    onLogout = onLogout,
                    onBookmarkClick = {
                        tab = Tab.HOME
                        homeVm.loadAndShowBookmarks()
                    },
                    onLicensesClick = onLicensesClick,
                )
            }
        }
        AdBanner(modifier = Modifier.fillMaxWidth())
        BottomTabs(active = tab, onSelect = { tab = it }, badge = unreadCount)
    }
}

/**
 * Android 13(API 33)+ 의 런타임 알림 권한(POST_NOTIFICATIONS)을 메인 진입 시 1회 요청한다.
 * 매니페스트 선언만으론 설치 시 알림이 기본 차단되므로 필요하다. 이미 허용됐거나 사용자가
 * 영구 거부한 경우엔 시스템이 다이얼로그를 띄우지 않는다(거부해도 앱은 정상 동작).
 */
@Composable
private fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun BottomTabs(active: Tab, onSelect: (Tab) -> Unit, badge: Int = 0) {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.bgSurface)
            .border(width = 1.dp, color = c.border)
            .navigationBarsPadding(),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            TabItem(Modifier.weight(1f), "홈", Icons.Filled.Home, active == Tab.HOME, 0) { onSelect(Tab.HOME) }
            TabItem(Modifier.weight(1f), "알림", Icons.Filled.Notifications, active == Tab.HISTORY, badge) { onSelect(Tab.HISTORY) }
            TabItem(Modifier.weight(1f), "설정", Icons.Filled.Settings, active == Tab.SETTINGS, 0) { onSelect(Tab.SETTINGS) }
        }
    }
}

@Composable
private fun TabItem(
    modifier: Modifier,
    label: String,
    icon: ImageVector,
    active: Boolean,
    badge: Int,
    onClick: () -> Unit,
) {
    val c = LocalAppColors.current
    val tint = if (active) c.accent else c.fgSubtle
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
            Text(
                label,
                color = tint,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        if (badge > 0) {
            val badgeText = if (badge > 99) "99+" else "$badge"
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp, start = 28.dp)
                    .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                    .zIndex(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.danger)
                    .padding(horizontal = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(badgeText, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
