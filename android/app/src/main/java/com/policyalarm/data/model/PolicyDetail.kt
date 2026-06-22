package com.policyalarm.data.model

import com.google.gson.annotations.SerializedName

data class PolicySummary(
    @SerializedName("what_changed") val whatChanged: String,
    @SerializedName("who_is_affected") val whoIsAffected: String,
    @SerializedName("when_effective") val whenEffective: String?,
    @SerializedName("key_points") val keyPoints: List<String>,
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
