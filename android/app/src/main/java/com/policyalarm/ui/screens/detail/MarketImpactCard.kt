package com.policyalarm.ui.screens.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.policyalarm.data.model.MarketContext
import com.policyalarm.data.model.MarketSector
import com.policyalarm.data.model.PolicyMarket
import com.policyalarm.data.model.ReactionAgg
import com.policyalarm.data.model.Ticker
import com.policyalarm.ui.components.Emoji
import com.policyalarm.ui.theme.AppColors
import com.policyalarm.ui.theme.LocalAppColors
import java.util.Locale

/** 시세 화면 링크. ETF도 같은 주소로 열린다. */
private fun quoteUrl(code: String) = "https://m.stock.naver.com/domestic/stock/$code/total"

/** 수익률 표기 "+1.2%". 한국 관례대로 상승=빨강, 하락=파랑으로 칠한다. */
internal fun formatPct(v: Double): String =
    (if (v > 0) "+" else "") + String.format(Locale.US, "%.1f", v * 100) + "%"

private fun pctColor(v: Double, c: AppColors): Color = when {
    v > 0 -> c.danger
    v < 0 -> c.accent
    else -> c.fgMuted
}

/**
 * 비교에 쓸 과거 집계를 고른다. 같은 방향으로 분류된 과거 정책 표본이 충분하면 그 집계,
 * 아니면 업종 전체 집계. 둘 다 부족하면 null(우연과 구분되지 않으므로 보여주지 않는다).
 * 반환: (집계, 같은 방향 집계인지)
 */
internal fun pickAgg(sector: MarketSector, ctx: MarketContext): Pair<ReactionAgg, Boolean>? {
    val stats = ctx.stats ?: return null
    val min = stats.minSamples
    val s = stats.sectors?.get(sector.sector) ?: return null
    val same = s.byDirection?.get(sector.direction)
    return when {
        same != null && same.count >= min && same.avgD5 != null -> same to true
        s.count >= min && s.avgD5 != null -> s.asAgg() to false
        else -> null
    }
}

@Composable
fun MarketImpactCard(market: PolicyMarket, ctx: MarketContext) {
    val c = LocalAppColors.current
    val sectors = market.sectors.orEmpty()
    if (sectors.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.bgSurface)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Emoji("📈", 17)
            Text("관련 산업·시장", color = c.fgStrong, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            market.policyType?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    color = c.fgSubtle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(c.bgMuted)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        market.investorNote?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = c.fgDefault, fontSize = 14.sp, lineHeight = 22.sp)
        }
        sectors.forEachIndexed { i, s ->
            Spacer(Modifier.height(if (i == 0) 14.dp else 18.dp))
            SectorBlock(s, ctx)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "투자 권유가 아닙니다. 정책과 관련될 수 있는 업종·기업을 AI와 편집 기준으로 정리한 참고 " +
                "정보이며, 과거 수익률은 미래 수익을 보장하지 않습니다. 투자 판단과 그 책임은 본인에게 있습니다.",
            color = c.fgFaint,
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(c.bgMuted)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectorBlock(s: MarketSector, ctx: MarketContext) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val (dirText, dirColor) = when (s.direction) {
        "긍정" -> "긍정적 영향 가능" to c.danger
        "부정" -> "부정적 영향 가능" to c.accent
        else -> "영향 엇갈림" to c.fgMuted
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(s.sector, color = c.fgStrong, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(
            dirText,
            color = dirColor,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(dirColor.copy(alpha = 0.1f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
    Spacer(Modifier.height(6.dp))
    Text(s.reason, color = c.fgDefault, fontSize = 14.sp, lineHeight = 22.sp)

    val tickers = s.etfs.orEmpty().map { it to true } + s.stocks.orEmpty().map { it to false }
    if (tickers.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tickers.forEach { (t, isEtf) -> TickerChip(t, isEtf) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(quoteUrl(t.code))))
            } }
        }
    }

    ctx.reactions[s.sector]?.d5?.let { d5 ->
        Spacer(Modifier.height(10.dp))
        Text(
            buildAnnotatedString {
                append("이 정책 발표 후 5거래일 업종 움직임 ")
                withStyle(SpanStyle(color = pctColor(d5, c), fontWeight = FontWeight.Bold)) { append(formatPct(d5)) }
                ctx.reactions[s.sector]?.x5?.let { x5 ->
                    append(" (KOSPI200 대비 ")
                    withStyle(SpanStyle(color = pctColor(x5, c), fontWeight = FontWeight.Bold)) { append(formatPct(x5)) }
                    append(")")
                }
            },
            color = c.fgMuted,
            fontSize = 12.5.sp,
        )
    }

    pickAgg(s, ctx)?.let { (agg, sameDir) ->
        val label = if (sameDir) "'${s.direction}'으로 분류된 ${s.sector} 정책" else "${s.sector} 관련 정책"
        Spacer(Modifier.height(10.dp))
        Text(
            buildAnnotatedString {
                append("과거 $label ")
                withStyle(SpanStyle(color = c.fgStrong, fontWeight = FontWeight.Bold)) { append("${agg.count}건") }
                append(" 발표 후 5거래일 평균 ")
                agg.avgD5?.let { withStyle(SpanStyle(color = pctColor(it, c), fontWeight = FontWeight.Bold)) { append(formatPct(it)) } }
                agg.avgX5?.let {
                    append(" · 시장 대비 ")
                    withStyle(SpanStyle(color = pctColor(it, c), fontWeight = FontWeight.Bold)) { append(formatPct(it)) }
                }
                agg.upRatioD5?.let { append(" · 상승 비율 ${(it * 100).toInt()}%") }
            },
            color = c.fgMuted,
            fontSize = 12.5.sp,
            lineHeight = 19.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(c.bgMuted)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        )
    }

    val disclosures = s.stocks.orEmpty()
        .flatMap { t -> ctx.disclosures[t.code].orEmpty().map { t.name to it } }
        .sortedByDescending { it.second.date }
        .take(5)
    if (disclosures.isNotEmpty()) {
        var expanded by remember { mutableStateOf(false) }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("발표 이후 관련 기업 공시 ${disclosures.size}건", color = c.accent, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                if (expanded) "접기" else "펼치기",
                tint = c.accent,
                modifier = Modifier.size(16.dp),
            )
        }
        if (expanded) {
            disclosures.forEach { (name, d) ->
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(name) }
                        append(" · ${d.title}  ")
                        withStyle(SpanStyle(color = c.fgFaint)) { append(d.date.drop(5).replace('-', '.')) }
                    },
                    color = c.fgDefault,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(d.url))) }
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun TickerChip(t: Ticker, isEtf: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Text(
        if (isEtf) "ETF · ${t.name}" else t.name,
        color = if (isEtf) c.accent else c.fgDefault,
        fontSize = 12.5.sp,
        fontWeight = if (isEtf) FontWeight.SemiBold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (isEtf) c.govTint else c.bgSurface)
            .border(1.dp, if (isEtf) c.accent.copy(alpha = 0.3f) else c.border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}
