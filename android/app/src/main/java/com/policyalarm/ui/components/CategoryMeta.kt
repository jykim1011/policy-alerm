package com.policyalarm.ui.components

import androidx.compose.ui.graphics.Color
import com.policyalarm.ui.theme.FileBlue
import com.policyalarm.ui.theme.FileGreen
import com.policyalarm.ui.theme.FileRed

/** Category metadata: emoji glyph + full descriptive label (from data.jsx). */
data class CategoryMeta(val key: String, val emoji: String, val full: String)

val CATEGORY_LIST = listOf(
    CategoryMeta("전체",   "📑", "전체"),
    CategoryMeta("부동산", "🏠", "부동산 전체"),
    CategoryMeta("청약",   "🔑", "청약 / 분양"),
    CategoryMeta("대출", "🏦", "대출 / 금리"),
    CategoryMeta("세금", "🧾", "세금 (취득세·종부세)"),
    CategoryMeta("재개발", "🏗️", "재개발 / 재건축"),
    CategoryMeta("전월세", "🏘️", "전·월세"),
    CategoryMeta("고용", "💼", "고용 / 취업"),
    CategoryMeta("복지", "🤝", "복지 / 지원금"),
    CategoryMeta("창업", "🚀", "창업 / 소상공인"),
    CategoryMeta("육아", "👶", "육아 / 보육"),
    CategoryMeta("교육", "📚", "교육 / 장학"),
    CategoryMeta("금융", "📈", "금융 / 투자"),
)

/** 홈 필터의 "내 관심" 칩 키. 온보딩·설정에서 구독한 카테고리만 모아 보여준다. */
const val INTEREST_FILTER = "관심"

/** Subscribable categories (everything except the "전체" filter). */
val SUBSCRIBABLE_CATEGORIES = CATEGORY_LIST.drop(1)

/**
 * 카테고리별 대표색. 카드 아이콘 배경(12% 알파)과 라벨 글자색에 써서 목록을 한눈에 구분되게 한다.
 * 라이트·다크 모두에서 읽히는 중간 채도 값만 쓴다. 모르는 키(전체·관심 등)는 null → 액센트색.
 */
fun categoryColor(key: String): Color? = when (key) {
    "부동산" -> Color(0xFF3182F6)
    "청약" -> Color(0xFF7B61FF)
    "대출" -> Color(0xFF05A578)
    "세금" -> Color(0xFFE09A00)
    "재개발" -> Color(0xFFB0762E)
    "전월세" -> Color(0xFF12A4A0)
    "고용" -> Color(0xFF0B9BDB)
    "복지" -> Color(0xFFF04452)
    "창업" -> Color(0xFFFF7A1A)
    "육아" -> Color(0xFFE5489B)
    "교육" -> Color(0xFF5B5FEF)
    "금융" -> Color(0xFF13B07A)
    else -> null
}

fun catMeta(key: String): CategoryMeta =
    CATEGORY_LIST.firstOrNull { it.key == key } ?: CATEGORY_LIST.first()

fun catEmoji(key: String): String = catMeta(key).emoji

/** File-type chip label + color (from FILE_TYPES in data.jsx). */
data class FileMeta(val label: String, val color: Color)

fun fileMeta(type: String?): FileMeta? = when (type?.lowercase()) {
    "hwp" -> FileMeta("HWP", FileBlue)
    "hwpx" -> FileMeta("HWPX", FileBlue)
    "pdf" -> FileMeta("PDF", FileRed)
    "html" -> FileMeta("HTML", FileGreen)
    else -> null
}
