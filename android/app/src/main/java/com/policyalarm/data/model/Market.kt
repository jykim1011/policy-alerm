package com.policyalarm.data.model

import com.google.gson.annotations.SerializedName

// 시장 영향 데이터 — 파이프라인 pipeline/market.py, market_stats.py 와 동일한 형태. 웹 lib/types.ts 참고.
// 업종은 LLM이 고르고, ETF·기업은 사람이 관리하는 매핑표(sector_map.json)에서만 붙는다.

data class Ticker(
    val code: String,
    val name: String,
)

data class MarketSector(
    val sector: String,
    /** "긍정" | "부정" | "혼재" */
    val direction: String,
    val reason: String,
    val etfs: List<Ticker>? = null,
    val stocks: List<Ticker>? = null,
)

data class MarketFigure(
    val label: String,
    val value: String,
)

data class PolicyMarket(
    val sectors: List<MarketSector>? = null,
    @SerializedName("policy_type") val policyType: String? = null,
    /** 신규 | 확대 | 연장 | 재확인 */
    val novelty: String? = null,
    /** 검토·계획 | 발표 | 입법예고 | 의결·통과 | 시행 */
    val stage: String? = null,
    val figures: List<MarketFigure>? = null,
    /** YYYY-MM-DD 또는 YYYY-MM */
    @SerializedName("effective_date") val effectiveDate: String? = null,
    /** 높음 | 보통 | 낮음 — 파이프라인 market.impact_of 규칙으로 계산 */
    val impact: String? = null,
    @SerializedName("investor_note") val investorNote: String? = null,
) {
    /** 카드를 보여줄지. 낮음(재확인·계획 단계 등)과 중요도 판정 전 데이터는 숨긴다. 파이프라인 SHOW_IMPACT 와 같은 기준. */
    val isShown: Boolean
        get() = !sectors.isNullOrEmpty() && impact in SHOW_IMPACT

    companion object {
        val SHOW_IMPACT = setOf("높음", "보통")
    }
}

/** docs/market/sector_stats.json — 업종별 과거 정책 발표 후 5거래일 반응 집계. */
data class ReactionAgg(
    val count: Int = 0,
    @SerializedName("avg_d5") val avgD5: Double? = null,
    @SerializedName("avg_x5") val avgX5: Double? = null,
    @SerializedName("up_ratio_d5") val upRatioD5: Double? = null,
)

data class SectorStats(
    val count: Int = 0,
    @SerializedName("avg_d5") val avgD5: Double? = null,
    @SerializedName("avg_x5") val avgX5: Double? = null,
    @SerializedName("up_ratio_d5") val upRatioD5: Double? = null,
    @SerializedName("by_direction") val byDirection: Map<String, ReactionAgg>? = null,
) {
    fun asAgg() = ReactionAgg(count, avgD5, avgX5, upRatioD5)
}

data class SectorStatsFile(
    @SerializedName("min_samples") val minSamples: Int = 3,
    val sectors: Map<String, SectorStats>? = null,
)

/** docs/market/reactions.json — 정책×업종 발표 후 수익률. key = "{policyId}|{업종}". */
data class PolicyReaction(
    @SerializedName("policy_id") val policyId: String,
    val sector: String,
    val d5: Double? = null,
    val x5: Double? = null,
)

data class ReactionsFile(
    val items: Map<String, PolicyReaction>? = null,
)

data class Disclosure(
    val title: String,
    val date: String,
    val url: String,
)

data class DisclosuresFile(
    val stocks: Map<String, List<Disclosure>>? = null,
)

/** docs/market/sectors.json — 관심 업종 설정 화면용 목록. */
data class SectorInfo(
    val key: String,
    val description: String,
)

data class SectorsFile(
    val sectors: List<SectorInfo>? = null,
)

/** 상세 화면 시장 카드에 필요한 부가 데이터. 각 파일은 독립적으로 실패할 수 있다(없으면 빈 값). */
data class MarketContext(
    val stats: SectorStatsFile? = null,
    /** 업종 → 이 정책 발표 후 반응 */
    val reactions: Map<String, PolicyReaction> = emptyMap(),
    /** 종목코드 → 정책 발표일 이후 공시(최신순) */
    val disclosures: Map<String, List<Disclosure>> = emptyMap(),
)

/** docs/market/calendar.json — 투자자용 모아보기(pipeline/market_calendar.py). */
data class CalendarEntry(
    @SerializedName("policy_id") val policyId: String,
    val title: String,
    @SerializedName("published_at") val publishedAt: String,
    val sectors: List<String>? = null,
    val impact: String? = null,
    val novelty: String? = null,
    val stage: String? = null,
    val figures: List<MarketFigure>? = null,
    @SerializedName("effective_date") val effectiveDate: String? = null,
    @SerializedName("date_exact") val dateExact: Boolean = false,
)

data class MomentumPolicy(
    @SerializedName("policy_id") val policyId: String,
    val title: String,
    @SerializedName("published_at") val publishedAt: String,
    val impact: String? = null,
    val direction: String? = null,
)

data class SectorMomentum(
    val sector: String,
    val count: Int = 0,
    val high: Int = 0,
    val positive: Int = 0,
    val negative: Int = 0,
    val mixed: Int = 0,
    val policies: List<MomentumPolicy>? = null,
)

data class MarketCalendar(
    @SerializedName("upcoming_days") val upcomingDays: Int = 180,
    @SerializedName("momentum_days") val momentumDays: Int = 28,
    val upcoming: List<CalendarEntry>? = null,
    val momentum: List<SectorMomentum>? = null,
)
