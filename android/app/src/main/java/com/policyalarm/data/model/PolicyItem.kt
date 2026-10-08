package com.policyalarm.data.model

import com.google.gson.annotations.SerializedName

data class PolicyItem(
    val id: String,
    val category: String,
    val subcategory: String,
    val title: String,
    val source: String?,
    @SerializedName("published_at") val publishedAt: String,
    @SerializedName("summary_preview") val summaryPreview: String,
    /** 시민 눈높이로 다시 쓴 제목(파이프라인 생성). 없는 옛 항목은 [displayTitle]이 원제목을 다듬어 쓴다. */
    @SerializedName("easy_title") val easyTitle: String? = null,
) {
    val displayTitle: String get() = easyTitle?.takeIf { it.isNotBlank() } ?: cleanTitle(title)
}

private val TITLE_TAG = Regex("""^\s*(\[[^\]]{1,12}]|\([^)]{1,12}\))\s*""")

/**
 * 보도자료 원제목 앞의 머리표("[사실은 이렇습니다]", "[보도자료]", "(설명)" 등)를 떼어낸다.
 * 쉬운 제목이 없는 정책도 카드에서 핵심부터 읽히게 하기 위함이다.
 */
fun cleanTitle(title: String): String {
    var t = title
    while (true) {
        val m = TITLE_TAG.find(t) ?: break
        t = t.substring(m.range.last + 1)
    }
    return t.trim().ifEmpty { title }
}

data class PolicyIndex(
    @SerializedName("updated_at") val updatedAt: String,
    val total: Int,
    val items: List<PolicyItem>,
)
