package com.policyalarm.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.policyalarm.data.model.PolicyItem
import com.policyalarm.ui.components.CATEGORY_LIST
import com.policyalarm.ui.components.CategoryChip
import com.policyalarm.ui.components.CategoryIcon
import com.policyalarm.ui.components.CommentCountBadge
import com.policyalarm.ui.components.INTEREST_FILTER
import com.policyalarm.ui.components.InlineAd
import com.policyalarm.ui.components.PolicyAppIcon
import com.policyalarm.ui.components.categoryColor
import com.policyalarm.ui.theme.LocalAppColors
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onPolicyClick: (String) -> Unit,
    onArchiveClick: () -> Unit = {},
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
        // top bar — 캔버스와 같은 배경으로 띄워 대시보드와 이어지게 한다.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.bgApp)
                .statusBarsPadding()
                .height(56.dp)
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PolicyAppIcon(size = 28, corner = 8)
            Spacer(Modifier.width(10.dp))
            Text(
                "정책 알리미",
                modifier = Modifier.weight(1f),
                color = c.fgStrong,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            TopIcon(Icons.Filled.DateRange, "정책 아카이브", onArchiveClick)
            TopIcon(Icons.Filled.Refresh, "새로고침") { vm.loadPolicies() }
        }

        if (state.showBookmarks) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "저장한 정책 ${state.bookmarkPolicies.size}개",
                    color = c.fgStrong,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TopIcon(Icons.Filled.Close, "북마크 닫기") { vm.exitBookmarksMode() }
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
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (!state.showBookmarks) {
                    val dash = state.dashboard()
                    item(key = "dashboard") {
                        DashboardCard(
                            dash = dash,
                            unreadOnly = state.unreadOnly,
                            onToggleUnread = vm::toggleUnreadOnly,
                        )
                    }
                    if (dash.weekByCategory.isNotEmpty()) {
                        item(key = "weekly") {
                            WeeklyCategories(
                                counts = dash.weekByCategory,
                                onSelect = vm::selectCategory,
                            )
                        }
                    }
                    // 분야 칩·부처 필터는 스크롤해도 위에 붙어 있어 언제든 바꿀 수 있다.
                    stickyHeader(key = "filters") {
                        FilterBar(
                            state = state,
                            onSelectCategory = vm::selectCategory,
                            onSelectSource = vm::selectSource,
                        )
                    }
                }

                if (state.policies.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            when {
                                state.unreadOnly -> "조건에 맞는 정책을 모두 읽었어요 👏"
                                state.selectedCategory == INTEREST_FILTER -> "최근 관심 분야 정책이 없어요\n설정에서 관심 카테고리를 늘려 보세요"
                                else -> "해당 조건의 최근 정책이 없어요"
                            },
                            color = c.fgMuted,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 8.dp),
                        )
                    }
                }

                // 발행일이 같은 정책끼리 "오늘/어제/M월 D일 (요일)" 섹션 하나(흰 카드)로 묶는다.
                var shown = 0
                var adPlaced = false
                dateGroups(state.policies).forEach { (label, groupItems) ->
                    item(key = "group-$label") {
                        PolicyGroup(
                            label = label,
                            items = groupItems,
                            readIds = state.readIds,
                            onPolicyClick = onPolicyClick,
                        )
                    }
                    shown += groupItems.size
                    // 피드 중간 인라인 광고 — 카드를 몇 장 본 뒤 섹션 사이에 한 번만, 북마크 모드 제외.
                    if (!state.showBookmarks && !adPlaced && shown >= FEED_AD_AFTER) {
                        adPlaced = true
                        item(key = "feed-ad") { InlineAd(Modifier.padding(top = 8.dp)) }
                    }
                }
                if (!state.showBookmarks) {
                    item(key = "archive-cta") {
                        // 목록 끝(최신 50개)에서 이탈하지 않도록 지난 정책 아카이브로 이어 준다.
                        com.policyalarm.ui.components.GhostButton(
                            onClick = onArchiveClick,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                        ) {
                            Icon(Icons.Filled.DateRange, null, tint = c.fgMuted, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("지난 정책 더 보기", color = c.fgDefault, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                item(key = "footer") {
                    Text(
                        "정책브리핑·각 부처 보도자료에서 자동 수집 · AI가 쉬운 말로 요약해요",
                        color = c.fgFaint,
                        fontSize = 11.5.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** 피드에서 이만큼의 정책을 보여준 뒤(섹션 경계에서) 인라인 광고를 한 번 넣는다. */
private const val FEED_AD_AFTER = 5

@Composable
private fun TopIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
) {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = c.fgMuted, modifier = Modifier.size(22.dp))
    }
}

/**
 * 홈 상단 브리핑 카드 — 오늘 새로 나온 건수, 이번 주 읽은 진행률, 안 읽은 것만 보기.
 * 목록만 있던 홈에 "오늘 확인할 게 얼마나 있는지"를 먼저 보여 줘서 들어올 이유를 만든다.
 */
@Composable
private fun DashboardCard(
    dash: HomeDashboard,
    unreadOnly: Boolean,
    onToggleUnread: () -> Unit,
) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(c.bgSurface)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(
            if (dash.todayCount > 0) "오늘 새로 나온 정책" else "이번 주 새로 나온 정책",
            color = c.fgSubtle,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = c.accent)) {
                    append("${if (dash.todayCount > 0) dash.todayCount else dash.weekCount}건")
                }
                append(if (dash.unreadCount > 0) ", 확인해 보세요" else ", 모두 읽었어요")
            },
            color = c.fgStrong,
            fontSize = 22.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold,
        )

        if (dash.weekCount > 0) {
            Spacer(Modifier.height(12.dp))
            val progress = dash.weekReadCount.toFloat() / dash.weekCount
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "이번 주 ${dash.weekCount}건 중 ${dash.weekReadCount}건 읽음",
                    color = c.fgMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${(progress * 100).toInt()}%",
                    color = c.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(c.bgMuted),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(c.accent),
                )
            }
        }

        if (dash.unreadCount > 0 || unreadOnly) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (unreadOnly) c.accent else c.govTint)
                    .clickable(onClick = onToggleUnread),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (unreadOnly) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    if (unreadOnly) "안 읽은 정책만 보는 중" else "안 읽은 정책 ${dash.unreadCount}건만 보기",
                    color = if (unreadOnly) Color.White else c.accent,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** "이번 주 분야별 소식" — 최근 7일 분야별 건수 타일. 누르면 그 분야로 필터한다. */
@Composable
private fun WeeklyCategories(
    counts: List<Pair<String, Int>>,
    onSelect: (String) -> Unit,
) {
    val c = LocalAppColors.current
    Column(Modifier.padding(top = 16.dp)) {
        Text(
            "이번 주 분야별 소식",
            color = c.fgStrong,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(counts, key = { it.first }) { (key, count) ->
                val tint = categoryColor(key) ?: c.accent
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.bgSurface)
                        .clickable { onSelect(key) }
                        .padding(start = 10.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryBadge(key, size = 30)
                    Spacer(Modifier.width(8.dp))
                    Text(key, color = c.fgDefault, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Text("$count", color = tint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** 분야 칩 + 주관부처 필터. 스크롤 시 상단에 붙는다. */
@Composable
private fun FilterBar(
    state: HomeUiState,
    onSelectCategory: (String) -> Unit,
    onSelectSource: (String) -> Unit,
) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.bgApp)
            .padding(top = 16.dp),
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // "전체" 바로 뒤에 내 구독 카테고리만 모은 "관심" 칩을 둔다(구독이 있을 때만).
            val chips = CATEGORY_LIST.map { it.key }.let { keys ->
                if (state.interests.isEmpty()) keys
                else listOf(keys.first(), INTEREST_FILTER) + keys.drop(1)
            }
            items(chips) { key ->
                CategoryChip(
                    label = key,
                    selected = state.selectedCategory == key,
                    onClick = { onSelectCategory(key) },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${state.policies.size}건",
                color = c.fgSubtle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            SourceFilter(
                sources = state.sources,
                selected = state.selectedSource,
                onSelect = onSelectSource,
            )
        }
    }
}

/** 날짜 섹션 하나 — 라벨 + 흰 카드 안에 구분선으로 나뉜 정책 행들. */
@Composable
private fun PolicyGroup(
    label: String,
    items: List<PolicyItem>,
    readIds: Set<String>,
    onPolicyClick: (String) -> Unit,
) {
    val c = LocalAppColors.current
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            label,
            color = c.fgStrong,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(c.bgSurface),
        ) {
            items.forEachIndexed { i, policy ->
                if (i > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 70.dp, end = 16.dp),
                        thickness = 1.dp,
                        color = c.border,
                    )
                }
                PolicyRow(
                    policy = policy,
                    isRead = policy.id in readIds,
                    showDate = false,
                    onClick = { onPolicyClick(policy.id) },
                )
            }
        }
    }
}

/** 카테고리 아이콘을 대표색 12% 배경의 둥근 사각형에 담는다. */
@Composable
fun CategoryBadge(key: String, size: Int = 44) {
    val c = LocalAppColors.current
    val tint = categoryColor(key) ?: c.accent
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.32f).dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        CategoryIcon(key, (size * 0.52f).dp, tint)
    }
}

/** 발행일 기준 피드 그룹 라벨 — 오늘/어제, 그 외 "M월 D일 (요일)". */
private fun dateGroupLabel(publishedAt: String): String {
    val date = runCatching { OffsetDateTime.parse(publishedAt).toLocalDate() }
        .getOrElse { return publishedAt.take(10) }
    val today = LocalDate.now()
    return when (java.time.temporal.ChronoUnit.DAYS.between(date, today)) {
        0L -> "오늘"
        1L -> "어제"
        else -> {
            val dow = "일월화수목금토"[date.dayOfWeek.value % 7]
            val year = if (date.year != today.year) "${date.year}년 " else ""
            "$year${date.monthValue}월 ${date.dayOfMonth}일 ($dow)"
        }
    }
}

/** 목록을 발행일 라벨 기준 연속 그룹으로 묶는다(순서 유지). */
private fun dateGroups(policies: List<PolicyItem>): List<Pair<String, List<PolicyItem>>> {
    val out = mutableListOf<Pair<String, MutableList<PolicyItem>>>()
    for (p in policies) {
        val label = dateGroupLabel(p.publishedAt)
        val last = out.lastOrNull()
        if (last != null && last.first == label) last.second.add(p)
        else out.add(label to mutableListOf(p))
    }
    return out
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

/**
 * 정책 한 줄 — 왼쪽 분야 배지, 오른쪽에 쉬운 제목·한 줄 요약·부처.
 * 읽은 정책은 제목을 흐리게 해 남은 것이 눈에 띄게 한다.
 */
@Composable
fun PolicyRow(
    policy: PolicyItem,
    isRead: Boolean,
    onClick: () -> Unit,
    showDate: Boolean = true,
) {
    val c = LocalAppColors.current
    val isNew = isNewPolicy(policy.publishedAt, isRead)
    val tint = categoryColor(policy.subcategory) ?: categoryColor(policy.category) ?: c.accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        CategoryBadge(policy.subcategory, size = 40)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(policy.subcategory, color = tint, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                if (isNew) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(5.dp).clip(CircleShape).background(c.danger))
                    Spacer(Modifier.width(3.dp))
                    Text("NEW", color = c.danger, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                policy.displayTitle,
                color = c.fgStrong,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 22.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(if (isRead) 0.5f else 1f),
            )
            if (policy.summaryPreview.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    policy.summaryPreview,
                    color = c.fgSubtle,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val meta = listOfNotNull(
                    policy.source?.takeIf { it.isNotBlank() },
                    policy.publishedAt.take(10).takeIf { showDate },
                ).joinToString(" · ")
                Text(meta, color = c.fgFaint, fontSize = 12.5.sp, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                CommentCountBadge(policy.id)
            }
        }
    }
}

/** 단독 카드 — 아카이브·상세 "함께 보면 좋은 정책" 등 섹션 밖에서 쓴다. */
@Composable
fun PolicyCard(policy: PolicyItem, isRead: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.bgSurface),
    ) {
        PolicyRow(policy = policy, isRead = isRead, onClick = onClick)
    }
}

/** 주관부처 필터 드롭다운. 현재 불러온 정책에 등장하는 부처만 빈도순으로 나열한다. */
@Composable
private fun SourceFilter(
    sources: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val c = LocalAppColors.current
    var expanded by remember { mutableStateOf(false) }
    val active = selected != "전체"
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (active) Modifier.background(c.govTint).border(1.dp, c.accent, RoundedCornerShape(8.dp))
                    else Modifier
                )
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = com.policyalarm.ui.components.BuildingIconVector,
                contentDescription = null,
                tint = if (active) c.accent else c.fgSubtle,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                if (active) selected else "주관부처 전체",
                color = if (active) c.accent else c.fgSubtle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = "주관부처 선택",
                tint = if (active) c.accent else c.fgSubtle,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(c.bgSurface),
        ) {
            DropdownMenuItem(
                text = { Text("전체", color = c.fgStrong, fontWeight = if (selected == "전체") FontWeight.Bold else FontWeight.Normal) },
                onClick = { onSelect("전체"); expanded = false },
            )
            sources.forEach { src ->
                DropdownMenuItem(
                    text = { Text(src, color = c.fgStrong, fontWeight = if (src == selected) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { onSelect(src); expanded = false },
                )
            }
        }
    }
}
