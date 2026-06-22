package com.policyalarm.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.policyalarm.BuildConfig
import com.policyalarm.ui.components.AppSwitch
import com.policyalarm.ui.components.Emoji
import com.policyalarm.ui.components.SUBSCRIBABLE_CATEGORIES
import com.policyalarm.ui.theme.LocalAppColors
import com.policyalarm.ui.theme.LocalThemeController

@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    onBookmarkClick: () -> Unit = {},
    onLicensesClick: () -> Unit = {},
    vm: SettingsViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val c = LocalAppColors.current
    val themeController = LocalThemeController.current

    LaunchedEffect(Unit) { vm.refreshBookmarkCount() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bgApp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.bgSurface)
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("설정", color = c.fgStrong, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // account header
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.bgSurface)
                    .border(1.dp, c.border, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF2F63F0), Color(0xFF1E3A8A)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        state.userName.take(1).ifBlank { "정" },
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.userName, color = c.fgStrong, fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
                    Text(
                        state.userEmail,
                        color = c.fgSubtle,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(c.bgMuted)
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) { Text("Google", color = c.fgSubtle, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
            }

            // 구독 카테고리
            SettingsSection("구독 카테고리") {
                SUBSCRIBABLE_CATEGORIES.forEachIndexed { i, cat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (i != SUBSCRIBABLE_CATEGORIES.lastIndex) Modifier.dividerBottom(c.border) else Modifier)
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Emoji(cat.emoji, 20)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(cat.key, color = c.fgStrong, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                            Text(cat.full, color = c.fgSubtle, fontSize = 11.5.sp)
                        }
                        AppSwitch(on = cat.key in state.subscribedCategories) { vm.toggleCategory(cat.key) }
                    }
                }
            }

            // 화면
            SettingsSection("화면") {
                SettingRow(dividerBelow = true) {
                    Emoji("🌙", 20)
                    Spacer(Modifier.width(12.dp))
                    Text("다크 모드", color = c.fgStrong, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    AppSwitch(on = themeController.isDark) { themeController.setDarkMode(it) }
                }
                SettingRow(onClick = onBookmarkClick) {
                    Emoji("🔖", 20)
                    Spacer(Modifier.width(12.dp))
                    Text("저장한 북마크", color = c.fgStrong, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${state.bookmarkCount}개", color = c.fgSubtle, fontSize = 13.sp)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.ChevronRight, null, tint = c.fgFaint, modifier = Modifier.size(18.dp))
                }
            }

            // 기타
            SettingsSection("기타") {
                SettingRow(dividerBelow = true, onClick = onLicensesClick) {
                    Text("오픈소스 라이선스", color = c.fgDefault, fontSize = 14.5.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ChevronRight, null, tint = c.fgFaint, modifier = Modifier.size(18.dp))
                }
                SettingRow(dividerBelow = true) {
                    Text("앱 버전", color = c.fgDefault, fontSize = 14.5.sp, modifier = Modifier.weight(1f))
                    Text(BuildConfig.VERSION_NAME, color = c.fgSubtle, fontSize = 13.sp)
                }
                SettingRow(onClick = { vm.logout(); onLogout() }) {
                    Text("로그아웃", color = c.danger, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                }
            }

            Text(
                "정책 알리미 · 정책 데이터는 각 부처 공개자료를 가공한 것으로\n법적 효력이 없습니다. 정확한 내용은 원문을 확인하세요.",
                color = c.fgFaint,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    val c = LocalAppColors.current
    Spacer(Modifier.height(22.dp))
    Text(
        title,
        color = c.fgSubtle,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 9.dp),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.bgSurface)
            .border(1.dp, c.border, RoundedCornerShape(16.dp)),
    ) { content() }
}

@Composable
private fun SettingRow(
    dividerBelow: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (dividerBelow) Modifier.dividerBottom(c.border) else Modifier)
            .heightIn(min = 30.dp)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A 1px bottom divider drawn inside the element's own bounds. */
private fun Modifier.dividerBottom(color: Color): Modifier = this.drawBehind {
    val stroke = 1.dp.toPx()
    drawLine(
        color = color,
        start = Offset(0f, size.height - stroke / 2f),
        end = Offset(size.width, size.height - stroke / 2f),
        strokeWidth = stroke,
    )
}
