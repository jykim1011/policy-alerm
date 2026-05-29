package com.policyalarm.data.model

import com.google.gson.annotations.SerializedName

data class PolicyItem(
    val id: String,
    val category: String,
    val subcategory: String,
    val title: String,
    val source: String,
    @SerializedName("published_at") val publishedAt: String,
    @SerializedName("summary_preview") val summaryPreview: String,
)

data class PolicyIndex(
    @SerializedName("updated_at") val updatedAt: String,
    val total: Int,
    val items: List<PolicyItem>,
)
