package com.policyalarm.data.model

import com.google.gson.annotations.SerializedName

data class FaqItem(
    val question: String,
    val answer: String,
)

data class GlossaryItem(
    val term: String,
    val definition: String,
)

data class PolicySummary(
    @SerializedName("what_changed") val whatChanged: String,
    @SerializedName("who_is_affected") val whoIsAffected: String,
    @SerializedName("when_effective") val whenEffective: String?,
    @SerializedName("key_points") val keyPoints: List<String>,
    // 시민 가치 보강 필드(선택) — 파이프라인이 채우면 상세 화면에 렌더된다. 웹의 PolicySummary 와 동일.
    val background: String? = null,
    val eligibility: List<String>? = null,
    @SerializedName("how_to_apply") val howToApply: String? = null,
    val faq: List<FaqItem>? = null,
    val glossary: List<GlossaryItem>? = null,
)

data class PolicyDetail(
    val id: String,
    val category: String,
    val subcategory: String,
    val title: String,
    val source: String,
    @SerializedName("source_url") val sourceUrl: String,
    @SerializedName("file_url") val fileUrl: String?,
    @SerializedName("file_type") val fileType: String?,
    @SerializedName("published_at") val publishedAt: String,
    @SerializedName("crawled_at") val crawledAt: String,
    val summary: PolicySummary?,
)
