package com.policyalarm.data.model

import com.google.gson.annotations.SerializedName

data class ArchiveYears(
    val years: List<Int>,
    @SerializedName("updated_at") val updatedAt: String,
)

data class YearArchive(
    val year: Int,
    val total: Int,
    @SerializedName("updated_at") val updatedAt: String,
    val items: List<PolicyItem>,
)
