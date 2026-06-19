package com.policyalarm.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.policyalarm.data.model.PolicyItem
import java.time.Instant
import java.time.OffsetDateTime
import com.policyalarm.ui.components.CATEGORY_LIST
import com.policyalarm.ui.components.CategoryChip
import com.policyalarm.ui.components.PolicyAppIcon
import com.policyalarm.ui.components.SubcatChip
import com.policyalarm.ui.theme.LocalAppColors

@Composable
fun HomeScreen(
    onPolicyClick: (String) -> Unit,
    vm: HomeViewModel,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val c = LocalAppColors.current

    // 북마크 모드에서 시스템 뒤로가기를 누르면 앱을 종료하지 않고 일반 홈으로 돌아간다.
    BackHandler(enabled = state.showBookmarks) { vm.exitBookmarksMode() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bgApp),
    ) {
        // top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.bgSurface)
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PolicyAppIcon(size = 30, corner = 8)
            Spacer(Modifier.width(12.dp))
            Text(
                "정책 알리미",
                modifier = Modifier.weight(1f),
                color = c.fgStrong,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { vm.loadPolicies() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Refresh, "새로고침", tint = c.fgMuted, modifier = Modifier.size(21.dp))
            }
        }

        if (state.showBookmarks) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.bgSurface)
                    .border(width = 1.dp, color = c.border)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "🔖 저장한 북마크 ${state.bookmarkPolicies.size}개",
                    color = c.fgStrong,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { vm.exitBookmarksMode() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, "북마크 닫기", tint = c.fgMuted, modifier = Modifier.size(18.dp))
                }
            }
        } else {
            // category chip row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.bgSurface)
                    .border(width = 1.dp, color = c.border),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(CATEGORY_LIST) { cat ->
                    CategoryChip(
                        label = cat.key,
                        emoji = if (cat.key == "전체") null else cat.emoji,
                        selected = state.selectedCategory == cat.key,
                        onClick = { vm.selectCategory(cat.key) },
                    )
                }
            }
        }

        when {
            state.isLoading && state.policies.isEmpty() -> Box(
                Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = c.accent) }

            state.error != null && state.policies.isEmpty() -> Box(
                Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error!!, color = c.fgMuted)
                    Spacer(Modifier.height(12.dp))
                    com.policyalarm.ui.components.PrimaryButton(
                        text = "다시 시도",
                        onClick = vm::loadPolicies,
                        modifier = Modifier.width(160.dp),
                        height = 44,
                    )
                }
            }

            state.showBookmarks && !state.isLoading && state.policies.isEmpty() -> Box(
                Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) { Text("저장한 북마크가 없어요", color = c.fgMuted) }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.policies, key = { it.id }) { policy ->
                    PolicyCard(
                        policy = policy,
                        isRead = policy.id in state.readIds,
                        onClick = { onPolicyClick(policy.id) },
                    )
                }
                item {
                    Text(
                        "정책브리핑·국토교통부 등에서 자동 수집 · 매일 오전 9시·오후 6시 업데이트",
                        color = c.fgFaint,
                        fontSize = 11.5.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** 발행 3일 이내이고 아직 읽지 않은 정책에만 NEW 뱃지를 표시한다. */
private fun isNewPolicy(publishedAt: String, isRead: Boolean): Boolean {
    if (isRead) return false
    return runCatching {
        val published = OffsetDateTime.parse(publishedAt).toInstant()
        val cutoff = Instant.now().minusSeconds(3L * 24 * 3600)
        published.isAfter(cutoff)
    }.getOrDefault(false)
}

@Composable
fun PolicyCard(policy: PolicyItem, isRead: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val isNew = isNewPolicy(policy.publishedAt, isRead)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.bgSurface)
            .border(1.dp, c.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .alpha(if (isRead) 0.62f else 1f)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SubcatChip(policy.category)
            if (isNew) {
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(6.dp).clip(CircleShape).background(c.danger)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("NEW", color = c.danger, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(policy.publishedAt.take(10), color = c.fgSubtle, fontSize = 12.sp)
        }
        Spacer(Modifier.height(9.dp))
        Text(
            policy.title,
            color = c.fgStrong,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 22.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            policy.summaryPreview,
            color = c.fgMuted,
            fontSize = 13.5.sp,
            lineHeight = 21.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(11.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(policy.source, color = c.fgDefault, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = c.fgFaint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
