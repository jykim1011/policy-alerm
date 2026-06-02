package com.policyalarm.ui.screens.main

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.policyalarm.ui.components.AdBanner
import com.policyalarm.ui.screens.history.HistoryScreen
import com.policyalarm.ui.screens.home.HomeScreen
import com.policyalarm.ui.screens.home.HomeViewModel
import com.policyalarm.ui.screens.home.HomeViewModelFactory
import com.policyalarm.ui.screens.settings.SettingsScreen
import com.policyalarm.ui.theme.LocalAppColors
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class Tab { HOME, HISTORY, SETTINGS }

@Composable
fun MainScaffold(
    onPolicyClick: (String) -> Unit,
    onLogout: () -> Unit,
) {
    val c = LocalAppColors.current
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bgApp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                Tab.HOME -> {
                    val vm = viewModel<HomeViewModel>(factory = HomeViewModelFactory(context))
                    HomeScreen(onPolicyClick = onPolicyClick, vm = vm)
                }
                Tab.HISTORY -> HistoryScreen(onPolicyClick = onPolicyClick)
                Tab.SETTINGS -> SettingsScreen(onLogout = onLogout)
            }
        }
        AdBanner(modifier = Modifier.fillMaxWidth())
        BottomTabs(active = tab, onSelect = { tab = it })
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
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp, start = 28.dp)
                    .size(16.dp)
                    .zIndex(1f)
                    .clip(CircleShape)
                    .background(c.danger),
                contentAlignment = Alignment.Center,
            ) {
                Text("$badge", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
