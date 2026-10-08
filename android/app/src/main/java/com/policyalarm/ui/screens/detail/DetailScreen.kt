package com.policyalarm.ui.screens.detail

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.policyalarm.ui.components.Emoji
import com.policyalarm.ui.components.FileChip
import com.policyalarm.ui.components.InlineAd
import com.policyalarm.ui.components.PrimaryButton
import com.policyalarm.ui.components.categoryColor
import com.policyalarm.ui.screens.home.CategoryBadge
import com.policyalarm.ui.screens.home.PolicyCard
import com.policyalarm.ui.theme.LocalAppColors

@Composable
fun DetailScreen(
    policyId: String,
    onBack: () -> Unit,
    vm: DetailViewModel,
    onPolicyClick: (String) -> Unit = {},
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = LocalAppColors.current

    LaunchedEffect(policyId) { vm.load(policyId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bgApp),
    ) {
        // top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.bgApp)
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircle(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", c.fgStrong, onBack)
            Spacer(Modifier.weight(1f))
            state.detail?.let { d ->
                IconCircle(Icons.Filled.Share, "공유하기", c.fgMuted) { sharePolicy(context, d) }
            }
            IconCircle(
                if (state.isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                "북마크",
                if (state.isBookmarked) c.accent else c.fgMuted,
            ) { vm.toggleBookmark(policyId) }
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.accent)
            }

            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.error!!, color = c.fgMuted)
            }

            state.detail != null -> {
                val detail = state.detail!!
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp),
                ) {
                    Spacer(Modifier.height(8.dp))
                    DetailHeader(detail)

                    val summary = detail.summary
                    if (summary != null) {
                        Spacer(Modifier.height(20.dp))
                        HighlightCard(summary.whatChanged)
                        Spacer(Modifier.height(12.dp))
                        summary.background?.takeIf { it.isNotBlank() }?.let {
                            BackgroundCard(it)
                            Spacer(Modifier.height(12.dp))
                        }
                        SummaryCard("👥", "누가 대상인가", summary.whoIsAffected)
                        Spacer(Modifier.height(12.dp))
                        // 시점이 아니라 서술("곧 발표될 예정입니다")인 값은 카드로 띄우지 않는다.
                        // 요약기에서도 걸러내지만, 이미 발행된 정책에는 소급되지 않아 화면에서도 막는다.
                        summary.whenEffective?.takeIf { it.any(Char::isDigit) }?.let {
                            SummaryCard("📅", "언제부터 적용되나", it)
                        }

                        if (summary.keyPoints.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            KeyPointsCard(summary.keyPoints)
                        }

                        summary.eligibility?.takeIf { it.isNotEmpty() }?.let {
                            Spacer(Modifier.height(12.dp))
                            EligibilityCard(it)
                        }
                        summary.howToApply?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(12.dp))
                            SummaryCard("📝", "신청 방법·기간", it)
                        }
                        summary.glossary?.takeIf { it.isNotEmpty() }?.let {
                            Spacer(Modifier.height(12.dp))
                            // 요약기 상한(5개)과 같은 값으로 자른다. 기존 발행분에는 43개까지
                            // 들어 있어 화면에서도 막아야 한다.
                            GlossaryCard(it.take(MAX_GLOSSARY))
                        }
                        summary.faq?.takeIf { it.isNotEmpty() }?.let {
                            Spacer(Modifier.height(12.dp))
                            FaqCard(it)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    // AI disclaimer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            null,
                            tint = c.accent,
                            modifier = Modifier.size(17.dp),
                        )
                        Text(
                            "AI가 원문을 요약했어요. 정확한 내용은 원문을 확인하세요.",
                            color = c.fgSubtle,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )
                    }
                    if (state.related.isNotEmpty()) {
                        Spacer(Modifier.height(28.dp))
                        Text(
                            "함께 보면 좋은 정책",
                            color = c.fgStrong,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            state.related.forEach { item ->
                                PolicyCard(
                                    policy = item,
                                    isRead = item.id in state.readIds,
                                    onClick = { onPolicyClick(item.id) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    InlineAd()
                    Spacer(Modifier.height(8.dp))
                    CommentSection(
                        threads = state.commentThreads,
                        commentCount = state.commentCount,
                        myUid = state.myUid,
                        onPost = { text, parentId, mention -> vm.postComment(policyId, text, parentId, mention) },
                        onDelete = { commentId -> vm.deleteComment(policyId, commentId) },
                    )
                    Spacer(Modifier.height(24.dp))
                }

                // bottom action bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.bgSurface)
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.bgMuted)
                            .clickable { sharePolicy(context, detail) }
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Filled.Share, null, tint = c.fgMuted, modifier = Modifier.size(18.dp))
                        Text("공유", color = c.fgDefault, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    PrimaryButton(
                        text = "원문 보기",
                        onClick = {
                            val url = detail.sourceUrl.ifBlank { detail.fileUrl }
                            if (url != null) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        height = 48,
                        leading = {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        },
                    )
                }
            }
        }
    }
}

/** 용어 풀이 노출 상한. 파이프라인 summarizer.MAX_GLOSSARY 와 같은 값. */
private const val MAX_GLOSSARY = 5

private const val WEB_BASE = "https://policy-alerm.web.app"

/**
 * 정책 내용을 공유한다. 링크는 Play 스토어가 아니라 웹 정책 상세 페이지로 보낸다 —
 * 받은 사람이 설치 없이 바로 내용을 읽을 수 있어야 열어보고, 그 페이지의 설치 배너가
 * 설치로 이어진다. 앱이 이미 깔린 사람은 App Links가 이 링크를 앱 상세로 연결한다.
 */
private fun sharePolicy(context: android.content.Context, detail: com.policyalarm.data.model.PolicyDetail) {
    val text = buildString {
        append("📋 ${detail.title}\n")
        append("🏛 ${detail.source} · ${detail.publishedAt.take(10)}")

        detail.summary?.whatChanged?.trim()?.takeIf { it.isNotEmpty() }?.let { changed ->
            val brief = if (changed.length > 150) changed.take(150).trimEnd() + "…" else changed
            append("\n\n🔄 무엇이 바뀌었나\n$brief")
        }
        detail.summary?.whenEffective?.trim()?.takeIf { it.isNotEmpty() }?.let { whenEff ->
            append("\n\n📅 적용 시기\n$whenEff")
        }

        append("\n\n──────────")
        append("\n📲 정책알람 — 청약·대출·창업·고용 등 새 정책을 가장 먼저")
        append("\n$WEB_BASE/policy/${Uri.encode(detail.id)}/")
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, detail.title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "공유하기"))
}

@Composable
private fun IconCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = tint, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun SummaryCard(emoji: String, label: String, body: String) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.bgSurface)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Emoji(emoji, 17)
            Text(label, color = c.fgStrong, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(body, color = c.fgDefault, fontSize = 15.5.sp, lineHeight = 25.sp)
    }
}

@Composable
private fun KeyPointsCard(points: List<String>) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.bgSurface)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Emoji("📌", 17)
            Text("핵심 포인트", color = c.fgStrong, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        points.forEachIndexed { i, pt ->
            Row(
                modifier = Modifier.padding(bottom = if (i == points.lastIndex) 0.dp else 11.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(c.govTint),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${i + 1}", color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text(pt, color = c.fgDefault, fontSize = 15.5.sp, lineHeight = 24.sp)
            }
        }
    }
}

/** 상단 헤더 — 분야·부처, 쉬운 제목(크게), 원문 제목(작게), 발표일·읽는 시간. */
@Composable
private fun DetailHeader(detail: com.policyalarm.data.model.PolicyDetail) {
    val c = LocalAppColors.current
    val tint = categoryColor(detail.subcategory) ?: categoryColor(detail.category) ?: c.accent
    Row(verticalAlignment = Alignment.CenterVertically) {
        CategoryBadge(detail.subcategory, size = 28)
        Spacer(Modifier.width(8.dp))
        Text(detail.subcategory, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("  ·  ${detail.source}", color = c.fgSubtle, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(8.dp))
        FileChip(detail.fileType)
    }
    Spacer(Modifier.height(14.dp))
    Text(
        detail.displayTitle,
        color = c.fgStrong,
        fontSize = 25.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
    )
    // 쉬운 제목을 보여줄 때는 보도자료 원제목을 작게 남겨 출처를 확인할 수 있게 한다.
    if (detail.displayTitle != detail.title) {
        Spacer(Modifier.height(8.dp))
        Text(
            "원문 제목 · ${detail.title}",
            color = c.fgFaint,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "${detail.publishedAt.take(10).replace('-', '.')} 발표 · ${readingMinutes(detail)}분이면 읽어요",
        color = c.fgSubtle,
        fontSize = 13.sp,
    )
}

/** 요약 분량으로 대략적인 읽는 시간(분)을 잰다. 한국어 평균 분당 500자 기준. */
private fun readingMinutes(detail: com.policyalarm.data.model.PolicyDetail): Int {
    val s = detail.summary ?: return 1
    val chars = listOfNotNull(s.whatChanged, s.whoIsAffected, s.whenEffective, s.background, s.howToApply)
        .sumOf { it.length } + s.keyPoints.sumOf { it.length } +
        (s.faq?.sumOf { it.question.length + it.answer.length } ?: 0)
    return (chars / 500).coerceAtLeast(1)
}

/** "한눈에 보기" — 무엇이 바뀌었는지를 액센트 카드로 가장 먼저 보여준다. */
@Composable
private fun HighlightCard(body: String) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.accent)
            .padding(20.dp),
    ) {
        Text("한눈에 보기", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(body, color = Color.White, fontSize = 16.5.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
    }
}

/** 배경 — 정책이 왜 나왔는지 도입부. 본문 위 강조 콜아웃으로 보여준다. */
@Composable
private fun BackgroundCard(text: String) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.govTint)
            .padding(start = 14.dp, top = 13.dp, end = 16.dp, bottom = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(c.accent),
        )
        Text(text, color = c.fgDefault, fontSize = 14.sp, lineHeight = 22.sp)
    }
}

/** 자가 체크 — "나에게 해당되나요?" 체크리스트. */
@Composable
private fun EligibilityCard(items: List<String>) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.bgSurface)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Emoji("✅", 17)
            Text("나에게 해당되나요?", color = c.fgStrong, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        items.forEachIndexed { i, item ->
            Row(
                modifier = Modifier.padding(bottom = if (i == items.lastIndex) 0.dp else 9.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text("✓", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(item, color = c.fgDefault, fontSize = 14.5.sp, lineHeight = 22.sp)
            }
        }
    }
}

/** 용어 풀이 — 용어(굵게) + 설명. */
@Composable
private fun GlossaryCard(items: List<com.policyalarm.data.model.GlossaryItem>) {
    val c = LocalAppColors.current
    // 용어 풀이는 요약 분량의 28%를 차지하는데(평균 459자) 정작 본문을 이해한 뒤
    // 필요할 때 보는 보조 정보다. FAQ와 같은 방식으로 접어 두고, 필요한 사람만 펼친다.
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.bgSurface)
            .clickable { expanded = !expanded }
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Emoji("📖", 17)
            Text("용어 풀이", color = c.fgStrong, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                "${items.size}개",
                color = c.fgFaint,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                if (expanded) "접기" else "펼치기",
                tint = c.fgFaint,
                modifier = Modifier.size(18.dp),
            )
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            items.forEachIndexed { i, g ->
                Column(modifier = Modifier.padding(bottom = if (i == items.lastIndex) 0.dp else 12.dp)) {
                    Text(g.term, color = c.fgStrong, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text(g.definition, color = c.fgSubtle, fontSize = 13.5.sp, lineHeight = 21.sp)
                }
            }
        }
    }
}

@Composable
private fun FaqCard(items: List<com.policyalarm.data.model.FaqItem>) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.bgSurface)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Emoji("💬", 17)
            Text("자주 묻는 질문", color = c.fgStrong, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        items.forEachIndexed { i, f ->
            var expanded by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (i == 0) 4.dp else 8.dp)
                    .clickable { expanded = !expanded },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Q", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(f.question, color = c.fgStrong, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
                }
                if (expanded) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("A", color = c.fgSubtle, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(f.answer, color = c.fgDefault, fontSize = 14.sp, lineHeight = 22.sp)
                    }
                }
            }
        }
    }
}
