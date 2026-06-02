package com.policyalarm.ui.components

import androidx.compose.ui.graphics.Color
import com.policyalarm.ui.theme.FileBlue
import com.policyalarm.ui.theme.FileGreen
import com.policyalarm.ui.theme.FileRed

/** Category metadata: emoji glyph + full descriptive label (from data.jsx). */
data class CategoryMeta(val key: String, val emoji: String, val full: String)

val CATEGORY_LIST = listOf(
    CategoryMeta("전체", "📑", "전체"),
    CategoryMeta("청약", "🔑", "청약 / 분양"),
    CategoryMeta("대출", "🏦", "대출 / 금리"),
    CategoryMeta("세금", "🧾", "세금 (취득세·종부세)"),
    CategoryMeta("재개발", "🏗️", "재개발 / 재건축"),
    CategoryMeta("전월세", "🏘️", "전·월세"),
)

/** Subscribable categories (everything except the "전체" filter). */
val SUBSCRIBABLE_CATEGORIES = CATEGORY_LIST.drop(1)

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
