package com.policyalarm.ui.screens.market

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.policyalarm.data.model.CalendarEntry
import com.policyalarm.data.model.MarketCalendar
import com.policyalarm.data.model.SectorMomentum
import com.policyalarm.ui.theme.LocalAppColors

/**
 * 시장 탭 — 투자자용 정책 모아보기. 다가오는 시행 일정과 최근 4주 업종별 정책 흐름.
 * 데이터: docs/market/calendar.json (중요도 보통 이상 정책만). 웹 /market 과 같은 구성.
 *
 * 색 규칙: 빨강·파랑은 영향 방향(긍정·부정)에만 쓴다. 중요도는 "주요" 잉크 배지 하나로만 표시한다.
 */
@Composable
fun MarketScreen(onPolicyClick: (String) -> Unit, vm: MarketViewModel = viewModel()) {
    val c = LocalAppColors.current
    val state by vm.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(c.bgApp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.bgApp)
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("시장", color = c.fgStrong, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.accent)
            }
            state.calendar == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "불러오지 못했어요. 눌러서 다시 시도",
                    color = c.fgMuted,
                    modifier = Modifier.clickable { vm.load() }.padding(16.dp),
                )
            }
            else -> MarketContent(state.calendar!!, onPolicyClick)
        }
    }
}

@Composable
private fun MarketContent(cal: MarketCalendar, onPolicyClick: (String) -> Unit) {
    val c = LocalAppColors.current
    val upcoming = cal.upcoming.orEmpty().filter { it.effectiveDate.orEmpty().length >= 7 }
    val momentum = cal.momentum.orEmpty()
    val weeks = cal.momentumDays / 7
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Text(
            "정책이 움직이는 업종,\n한눈에 모아봤어요",
            color = c.fgStrong,
            fontSize = 23.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "새로 나왔거나 규모가 큰 정책만 골랐어요. 재확인 발표나 계획 단계 정책은 빠져요.",
            color = c.fgSubtle,
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
        )

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val top = momentum.firstOrNull()
            Stat("시행 예정", "${upcoming.size}건", "${cal.upcomingDays}일 안", Modifier.weight(1f))
            Stat("주요 정책", "${upcoming.count { it.impact == "높음" }}건", "신규·대규모", Modifier.weight(1f))
            Stat("최다 업종", top?.sector ?: "-", top?.let { "최근 ${weeks}주 ${it.count}건" } ?: "", Modifier.weight(1f))
        }

        SectionLabel("다가오는 시행 일정")
        if (upcoming.isEmpty()) {
            EmptyCard("앞으로 ${cal.upcomingDays}일 안에 시행되는 정책이 아직 없어요.")
        }
        upcoming.forEach {
            UpcomingCard(it) { onPolicyClick(it.policyId) }
            Spacer(Modifier.height(10.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("최근 ${weeks}주 업종별 정책 흐름", Modifier.weight(1f))
            Legend("▲", c.danger, "긍정")
            Spacer(Modifier.width(8.dp))
            Legend("◆", c.fgFaint, "엇갈림")
            Spacer(Modifier.width(8.dp))
            Legend("▼", c.accent, "부정")
        }
        if (momentum.isEmpty()) EmptyCard("최근 업종 관련 정책이 없어요.")
        val maxCount = (momentum.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
        momentum.forEach {
            MomentumCard(it, maxCount, onPolicyClick)
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "투자 권유가 아닙니다. 정부 보도자료를 바탕으로 정책과 관련될 수 있는 업종을 AI와 편집 기준으로 " +
                "정리한 참고 정보이며, 시행 일정은 바뀔 수 있으니 원문을 확인하세요. 투자 판단과 그 책임은 본인에게 있습니다.",
            color = c.fgFaint,
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Text(
        text,
        color = c.fgMuted,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 26.dp, bottom = 10.dp),
    )
}

@Composable
private fun Legend(mark: String, color: Color, label: String) {
    val c = LocalAppColors.current
    Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(mark, color = color, fontSize = 9.sp)
        Spacer(Modifier.width(3.dp))
        Text(label, color = c.fgFaint, fontSize = 11.5.sp)
    }
}

@Composable
private fun SurfaceCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val c = LocalAppColors.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.bgSurface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(18.dp),
    ) { content() }
}

@Composable
private fun EmptyCard(text: String) {
    val c = LocalAppColors.current
    SurfaceCard {
        Text(text, color = c.fgMuted, fontSize = 13.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
    }
}

@Composable
private fun Stat(label: String, value: String, sub: String, modifier: Modifier) {
    val c = LocalAppColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(c.bgSurface)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text(label, color = c.fgFaint, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(value, color = c.fgStrong, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sub, color = c.fgSubtle, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Pill(text: String, fg: Color, bg: Color) {
    Text(
        text,
        color = fg,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** "12월 1일" / 월만 알면 "12월 중" */
private fun whenLabel(e: CalendarEntry): String {
    val eff = e.effectiveDate.orEmpty()
    val month = eff.substring(5, 7).toIntOrNull() ?: 0
    return if (e.dateExact && eff.length == 10) "${month}월 ${eff.substring(8, 10).toInt()}일" else "${month}월 중"
}

@Composable
private fun UpcomingCard(e: CalendarEntry, onClick: () -> Unit) {
    val c = LocalAppColors.current
    SurfaceCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("${whenLabel(e)} 시행", c.accent, c.govTint)
            if (e.impact == "높음") Pill("주요", Color.White, c.fgStrong)
            Spacer(Modifier.weight(1f))
            Text(listOfNotNull(e.novelty, e.stage).joinToString(" · "), color = c.fgFaint, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(e.title, color = c.fgStrong, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                e.sectors.orEmpty().forEach { SectorChip(it) }
            }
            e.figures?.firstOrNull()?.let { f ->
                Column(Modifier.widthIn(max = 170.dp), horizontalAlignment = Alignment.End) {
                    Text(f.label, color = c.fgFaint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    // 긴 수치("1057세대 중 284세대")는 한 단계 작게
                    Text(
                        f.value,
                        color = c.fgStrong,
                        fontSize = if (f.value.length > 9) 14.sp else 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.End,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectorChip(name: String) {
    val c = LocalAppColors.current
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(c.bgMuted)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.ShowChart, null, tint = c.fgFaint, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(name, color = c.fgDefault, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MomentumCard(m: SectorMomentum, maxCount: Int, onPolicyClick: (String) -> Unit) {
    val c = LocalAppColors.current
    SurfaceCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(m.sector, color = c.fgStrong, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${m.count}", color = c.fgStrong, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    Text("건", color = c.fgMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp, bottom = 4.dp))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (m.high > 0) Text("주요 ${m.high}", color = c.fgStrong, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("긍정 ${m.positive} · 부정 ${m.negative}", color = c.fgFaint, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        // 막대 길이 = 건수(최다 업종 대비), 색 구간 = 영향 방향 비율
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(c.bgMuted)) {
            Row(Modifier.fillMaxWidth(m.count.toFloat() / maxCount).fillMaxHeight().clip(RoundedCornerShape(50))) {
                listOf(m.positive to c.danger, m.mixed to c.borderStrong, m.negative to c.accent)
                    .filter { it.first > 0 }
                    .forEach { (n, color) -> Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(color)) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Spacer(Modifier.height(8.dp))
        // 건수와 목록이 어긋나지 않게 3건만 먼저 보이고 나머지는 펼쳐 본다.
        val policies = m.policies.orEmpty()
        var expanded by remember(m.sector) { mutableStateOf(false) }
        (if (expanded) policies else policies.take(VISIBLE)).forEach { p ->
            Row(
                Modifier.fillMaxWidth().clickable { onPolicyClick(p.policyId) }.padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DirectionMark(p.direction)
                Spacer(Modifier.width(6.dp))
                Text(p.publishedAt.drop(5).replace('-', '.'), color = c.fgFaint, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Text(p.title, color = c.fgDefault, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (policies.size > VISIBLE) {
            Text(
                if (expanded) "접기" else "나머지 ${policies.size - VISIBLE}건 더 보기",
                color = c.accent,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { expanded = !expanded }.padding(top = 6.dp, bottom = 2.dp),
            )
        }
    }
}

private const val VISIBLE = 3

/** 정책별 영향 방향 표시. 색은 막대와 같다(빨강 긍정·회색 엇갈림·파랑 부정). */
@Composable
private fun DirectionMark(direction: String?) {
    val c = LocalAppColors.current
    val (mark, color) = when (direction) {
        "긍정" -> "▲" to c.danger
        "부정" -> "▼" to c.accent
        else -> "◆" to c.fgFaint
    }
    Text(mark, color = color, fontSize = 10.sp, modifier = Modifier.width(12.dp))
}
