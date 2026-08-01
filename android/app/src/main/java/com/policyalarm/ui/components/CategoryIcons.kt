package com.policyalarm.ui.components

// 정책알람 커스텀 아이콘 세트 — 웹(components/icons.tsx)과 동일한 디자인 언어.
// 24px 그리드, 1.7px 라운드 스트로크 + 12% 듀오톤 필. 검정으로 그리고 Icon tint 로 물들인다.

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private fun ImageVector.Builder.stroke(d: String, width: Float = 1.7f) {
    addPath(
        pathData = addPathNodes(d),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}

/** 듀오톤 면 — 스트로크 없는 12% 필. */
private fun ImageVector.Builder.tone(d: String) {
    addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black), fillAlpha = 0.12f)
}

private fun ImageVector.Builder.filled(d: String) {
    addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black))
}

private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()

private fun circleD(cx: Float, cy: Float, r: Float): String =
    "M ${cx - r} $cy a $r $r 0 1 0 ${2 * r} 0 a $r $r 0 1 0 ${-2 * r} 0"

private fun roundRectD(x: Float, y: Float, w: Float, h: Float, rx: Float): String =
    "M ${x + rx} $y h ${w - 2 * rx} a $rx $rx 0 0 1 $rx $rx v ${h - 2 * rx} " +
        "a $rx $rx 0 0 1 -$rx $rx h ${-(w - 2 * rx)} a $rx $rx 0 0 1 -$rx -$rx " +
        "v ${-(h - 2 * rx)} a $rx $rx 0 0 1 $rx -$rx Z"

private val AllIcon by lazy {
    icon("전체") {
        stroke(roundRectD(4.2f, 4.2f, 6.6f, 6.6f, 2f))
        stroke(roundRectD(13.2f, 4.2f, 6.6f, 6.6f, 2f))
        stroke(roundRectD(4.2f, 13.2f, 6.6f, 6.6f, 2f))
        filled(roundRectD(13.2f, 13.2f, 6.6f, 6.6f, 2f))
    }
}

private const val HOUSE =
    "M4.5 10.3 12 4.2l7.5 6.1V19a1.4 1.4 0 0 1-1.4 1.4H5.9A1.4 1.4 0 0 1 4.5 19Z"

private val HouseIcon by lazy {
    icon("부동산") {
        tone(HOUSE)
        stroke(HOUSE)
        stroke("M9.9 20.2v-4a2.1 2.1 0 0 1 4.2 0v4")
    }
}

private val KeyIcon by lazy {
    icon("청약") {
        tone(circleD(8.6f, 8.6f, 3.9f))
        stroke(circleD(8.6f, 8.6f, 3.9f))
        filled(circleD(8.6f, 8.6f, 0.9f))
        stroke("m11.4 11.4 8.2 8.2")
        stroke("m15.3 15.3 2-2")
        stroke("m18.1 18.1 2-2")
    }
}

private val CoinIcon by lazy {
    icon("대출") {
        tone(circleD(12f, 12f, 8f))
        stroke(circleD(12f, 12f, 8f))
        stroke("m8.4 9.4 1.2 5.2 2.4-4.6 2.4 4.6 1.2-5.2", width = 1.5f)
        stroke("M7.7 11.9h8.6", width = 1.5f)
    }
}

private const val RECEIPT =
    "M6.8 18.4V3.6h10.4v14.8l-2.08-1.5-2.08 1.5-2.08-1.5-2.08 1.5-2.08-1.5Z"

private val ReceiptIcon by lazy {
    icon("세금") {
        tone(RECEIPT)
        stroke(RECEIPT)
        stroke("M9.6 8.1h4.8M9.6 11.4h4.8")
    }
}

private val CraneIcon by lazy {
    icon("재개발") {
        tone("M5.2 11h6.6v9.2H5.2Z")
        stroke("M5.2 20.2V11h6.6v9.2")
        stroke("M3.9 20.2h16.2")
        stroke("M7.2 14.1h2.6M7.2 17h2.6")
        stroke("M8.5 11V5.3h10.7")
        stroke("M16.7 5.3v3")
        stroke(circleD(16.7f, 9.2f, 0.9f))
    }
}

private val RentIcon by lazy {
    icon("전월세") {
        tone(HOUSE)
        stroke(HOUSE)
        stroke("M9.2 13.1h5.6l-1.6-1.6")
        stroke("M14.8 16.3H9.2l1.6 1.6")
    }
}

private val BriefcaseIcon by lazy {
    icon("고용") {
        tone(roundRectD(4.2f, 7.8f, 15.6f, 11.4f, 2.2f))
        stroke(roundRectD(4.2f, 7.8f, 15.6f, 11.4f, 2.2f))
        stroke("M9.7 7.8V6.3a2 2 0 0 1 2-2h.6a2 2 0 0 1 2 2v1.5")
        stroke("M4.2 12.6h15.6")
        stroke("M10.8 12.6v1.9h2.4v-1.9")
    }
}

private const val HEART =
    "M12 18.9C9 16.9 5.6 14 5.6 10.4a3.6 3.6 0 0 1 6.4-2.2 3.6 3.6 0 0 1 6.4 2.2c0 3.6-3.4 6.5-6.4 8.5Z"

private val HeartIcon by lazy {
    icon("복지") {
        tone(HEART)
        stroke(HEART)
        stroke("M18.4 4.2v2.4M17.2 5.4h2.4", width = 1.4f)
    }
}

private const val ROCKET =
    "M12 3.4c2.9 1.9 4.3 4.9 4.3 8a12 12 0 0 1-.9 4.5H8.6a12 12 0 0 1-.9-4.5c0-3.1 1.4-6.1 4.3-8Z"

private val RocketIcon by lazy {
    icon("창업") {
        tone(ROCKET)
        stroke(ROCKET)
        stroke(circleD(12f, 10.2f, 1.7f))
        stroke("M8.6 15.9 6.9 19.2l2.9-1.1M15.4 15.9l1.7 3.3-2.9-1.1")
        stroke("M12 18.4v2.2")
    }
}

private val PramIcon by lazy {
    icon("육아") {
        tone("M4.6 10.2h14.8v.9a6 6 0 0 1-6 6h-2.8a6 6 0 0 1-6-6Z")
        tone("M4.6 10.2a7.6 7.6 0 0 1 7.6-7.6v7.6Z")
        stroke("M4.6 10.2h14.8v.9a6 6 0 0 1-6 6h-2.8a6 6 0 0 1-6-6Z")
        stroke("M4.6 10.2a7.6 7.6 0 0 1 7.6-7.6v7.6")
        stroke(circleD(8.3f, 19.7f, 1.4f))
        stroke(circleD(15.7f, 19.7f, 1.4f))
    }
}

private val GradCapIcon by lazy {
    icon("교육") {
        tone("M12 4.3 21.2 8.4 12 12.5 2.8 8.4Z")
        stroke("M12 4.3 21.2 8.4 12 12.5 2.8 8.4Z")
        stroke("M6.6 10.7v3.6c0 1.3 2.4 2.7 5.4 2.7s5.4-1.4 5.4-2.7v-3.6")
        stroke("M21.2 8.4v4.6")
        filled(circleD(21.2f, 14.2f, 0.8f))
    }
}

private val ChartIcon by lazy {
    icon("금융") {
        tone("M4.2 17.6l4.6-4.6 3.4 2.9 7.6-7.3v10.8H4.2Z")
        stroke("m4.2 17.6 4.6-4.6 3.4 2.9 7.6-7.3")
        stroke("M15.9 8.2h3.9v3.9")
    }
}

/** 주관부처(정부기관) — 페디먼트 + 기둥. */
val BuildingIconVector: ImageVector by lazy {
    icon("주관부처") {
        tone("M12 3.6 20.2 8.4H3.8Z")
        stroke("M12 3.6 20.2 8.4H3.8Z")
        stroke("M6.6 11v6.2M12 11v6.2M17.4 11v6.2")
        stroke("M4.4 19.8h15.2")
    }
}

/** 말풍선 — 목록 카드 댓글 수 배지. */
val ChatIconVector: ImageVector by lazy {
    icon("댓글") {
        stroke(
            "M12 4.3c4.6 0 8 2.9 8 6.5s-3.4 6.5-8 6.5c-.9 0-1.8-.1-2.6-.3l-3.3 2.1" +
                "a.7.7 0 0 1-1.05-.75l.55-2.6C4.6 14.6 4 12.8 4 10.8c0-3.6 3.4-6.5 8-6.5Z"
        )
    }
}

fun categoryIconVector(key: String): ImageVector = when (key) {
    "부동산" -> HouseIcon
    "청약" -> KeyIcon
    "대출" -> CoinIcon
    "세금" -> ReceiptIcon
    "재개발" -> CraneIcon
    "전월세" -> RentIcon
    "고용" -> BriefcaseIcon
    "복지" -> HeartIcon
    "창업" -> RocketIcon
    "육아" -> PramIcon
    "교육" -> GradCapIcon
    "금융" -> ChartIcon
    else -> AllIcon
}

/** 카테고리 커스텀 아이콘 — 이모지(catEmoji) 대체. */
@Composable
fun CategoryIcon(
    key: String,
    size: Dp,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = categoryIconVector(key),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size),
    )
}
