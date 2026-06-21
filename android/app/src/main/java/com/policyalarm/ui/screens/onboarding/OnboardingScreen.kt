package com.policyalarm.ui.screens.onboarding

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.policyalarm.ui.components.CategoryMeta
import com.policyalarm.ui.components.Emoji
import com.policyalarm.ui.components.PrimaryButton
import com.policyalarm.ui.components.SUBSCRIBABLE_CATEGORIES
import com.policyalarm.ui.theme.LocalAppColors

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    vm: OnboardingViewModel = viewModel(),
) {
    val selected by vm.selected.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()
    val c = LocalAppColors.current

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // 권한 허용 여부와 무관하게 온보딩을 완료한다.
        // 거부 시에도 인앱 알림(Firestore 기반 뱃지)은 정상 동작하며,
        // 푸시 알림은 설정 앱에서 권한을 허용하면 자동으로 활성화된다.
        vm.confirm(isGranted)
    }

    LaunchedEffect(done) { if (done) onComplete() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bgApp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            Text(
                "관심 있는 정책을\n선택하세요",
                color = c.fgStrong,
                fontSize = 23.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "선택한 분야의 새 정책만 알림으로 보내드려요. 나중에 설정에서 바꿀 수 있어요.",
                color = c.fgMuted,
                fontSize = 13.5.sp,
                lineHeight = 20.sp,
            )

            Spacer(Modifier.height(22.dp))
            // 2-column grid
            SUBSCRIBABLE_CATEGORIES.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowItems.forEach { cat ->
                        CategoryCard(
                            cat = cat,
                            selected = cat.key in selected,
                            onClick = { vm.toggle(cat.key) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(14.dp))
            Text("알림 받을 시간", color = c.fgStrong, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("정책은 보통 평일 오전·오후에 발표돼요", color = c.fgSubtle, fontSize = 12.5.sp)
            Spacer(Modifier.height(12.dp))
            com.policyalarm.ui.components.Segmented(
                value = schedule,
                options = listOf("morning" to "오전 9시", "evening" to "오후 6시", "both" to "둘 다"),
                onChange = vm::setSchedule,
            )
            Spacer(Modifier.height(20.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.bgApp)
                .border(width = 1.dp, color = c.border)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            PrimaryButton(
                text = if (selected.isEmpty()) "한 개 이상 선택하세요"
                else "${selected.size}개 분야로 시작하기",
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        vm.confirm()
                    }
                },
                enabled = selected.isNotEmpty(),
            )
        }
    }
}

@Composable
private fun CategoryCard(
    cat: CategoryMeta,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(c.bgSurface)
            .border(
                width = 2.dp,
                color = if (selected) c.accent else c.border,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 16.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) c.govTint else c.bgMuted),
                contentAlignment = Alignment.Center,
            ) { Emoji(cat.emoji, 22) }
            Spacer(Modifier.height(11.dp))
            Text(cat.key, color = c.fgStrong, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(cat.full, color = c.fgSubtle, fontSize = 11.5.sp)
        }
        // check indicator top-right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) c.accent else c.bgSurface)
                .border(
                    width = 2.dp,
                    color = if (selected) c.accent else c.borderStrong,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}
