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

data class PolicyMarket(
    val sectors: List<MarketSector>? = null,
    @SerializedName("policy_type") val policyType: String? = null,
    @SerializedName("investor_note") val investorNote: String? = null,
)

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
