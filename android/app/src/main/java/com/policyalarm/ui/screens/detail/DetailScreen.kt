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
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.policyalarm.ui.components.PrimaryButton
import com.policyalarm.ui.components.SubcatChip
import com.policyalarm.ui.theme.LocalAppColors

@Composable
fun DetailScreen(
    policyId: String,
    onBack: () -> Unit,
    vm: DetailViewModel,
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
                .background(c.bgSurface)
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircle(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", c.fgMuted, onBack)
            Text(
                "정책 상세",
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                color = c.fgMuted,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
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
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SubcatChip(detail.category)
                        Text(detail.source, color = c.fgDefault, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        FileChip(detail.fileType)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        detail.title,
                        color = c.fgStrong,
                        fontSize = 22.sp,
                        lineHeight = 31.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${detail.publishedAt.take(10)} 발표",
                        color = c.fgSubtle,
                        fontSize = 12.5.sp,
                    )

                    val summary = detail.summary
                    if (summary != null) {
                        Spacer(Modifier.height(20.dp))
                        SummaryCard("🔄", "무엇이 바뀌었나", summary.whatChanged)
                        Spacer(Modifier.height(12.dp))
                        SummaryCard("👥", "누가 대상인가", summary.whoIsAffected)
                        Spacer(Modifier.height(12.dp))
                        SummaryCard("📅", "언제부터 적용되나", summary.whenEffective)

                        if (summary.keyPoints.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            KeyPointsCard(summary.keyPoints)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    // AI disclaimer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.bgSurface2)
                            .border(1.dp, c.borderStrong, RoundedCornerShape(8.dp))
                            .padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Emoji("🤖", 17)
                        Text(
                            "AI가 원문을 요약했어요. 정확한 내용은 원문을 확인하세요.",
                            color = c.fgSubtle,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // bottom action bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.bgApp)
                        .border(width = 1.dp, color = c.border)
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 52.dp, height = 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.bgSurface)
                            .border(
                                1.dp,
                                if (state.isBookmarked) c.accent else c.borderStrong,
                                RoundedCornerShape(8.dp),
                            )
                            .clickable { vm.toggleBookmark(policyId) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (state.isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            "북마크",
                            tint = if (state.isBookmarked) c.accent else c.fgMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    PrimaryButton(
                        text = "원문 보기",
                        onClick = {
                            val url = detail.fileUrl ?: detail.sourceUrl
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        modifier = Modifier.weight(1f),
                        height = 48,
                        leading = {
                            Icon(Icons.Filled.OpenInNew, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        },
                    )
                }
            }
        }
    }
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
            .clip(RoundedCornerShape(16.dp))
            .background(c.bgSurface)
            .border(1.dp, c.border, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Emoji(emoji, 15)
            Text(label, color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(body, color = c.fgDefault, fontSize = 14.5.sp, lineHeight = 23.sp)
    }
}

@Composable
private fun KeyPointsCard(points: List<String>) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.bgSurface)
            .border(1.dp, c.border, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text("핵심 포인트", color = c.fgStrong, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                Text(pt, color = c.fgDefault, fontSize = 14.5.sp, lineHeight = 22.sp)
            }
        }
    }
}
