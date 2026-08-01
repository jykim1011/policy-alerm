package com.policyalarm.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================
// 통합 커뮤니티 Design System — raw color tokens
// (deep-blue government / trust accent; light + dark roles)
// Ported from ds-tokens.css + app.css
// =============================================================

// ---- Neutrals (Tailwind gray) ----
val Gray50 = Color(0xFFF9FAFB)
val Gray100 = Color(0xFFF3F4F6)
val Gray200 = Color(0xFFE5E7EB)
val Gray300 = Color(0xFFD1D5DB)
val Gray400 = Color(0xFF9CA3AF)
val Gray500 = Color(0xFF6B7280)
val Gray600 = Color(0xFF4B5563)
val Gray700 = Color(0xFF374151)
val Gray800 = Color(0xFF1F2937)
val Gray900 = Color(0xFF111827)
val Gray950 = Color(0xFF030712)

// ---- Brand / action (blue) ----
val Blue50 = Color(0xFFEFF6FF)
val Blue500 = Color(0xFF3B82F6)
val Blue600 = Color(0xFF2563EB)
val GovBlue = Color(0xFF1D4ED8)      // blue-700 — primary action (light)
val GovBlueDeepC = Color(0xFF1E3A8A) // blue-900 — splash / headers
val GovBlueInk = Color(0xFF172554)   // blue-950
val Blue400 = Color(0xFF60A5FA)
val Blue800 = Color(0xFF1E40AF)

// ---- Semantic accents ----
val Red500 = Color(0xFFEF4444)
val Amber400 = Color(0xFFFBBF24)

// ---- File-type chip colors ----
val FileBlue = Color(0xFF2563EB) // HWP / HWPX
val FileRed = Color(0xFFDC2626)  // PDF
val FileGreen = Color(0xFF16A34A) // HTML

// ---- Light role colors — 웹(globals.css)과 동일한 토스풍 팔레트 ----
// 연회색 캔버스(#F2F4F6) 위 흰 카드 + 토스블루(#3182F6) 액센트 + 토스 그레이 글자.
val LightBgApp = Color(0xFFF2F4F6)
val LightBgSurface = Color(0xFFFFFFFF)
val LightBgSurface2 = Color(0xFFF2F4F6)
val LightBgMuted = Color(0xFFF2F4F6)
val LightFgStrong = Color(0xFF191F28)  // toss gray900
val LightFgDefault = Color(0xFF333D4B) // gray800
val LightFgMuted = Color(0xFF4E5968)   // gray700
val LightFgSubtle = Color(0xFF6B7684)  // gray600 — 흰 배경 대비 AA
val LightFgFaint = Color(0xFF8B95A1)   // gray500
val LightBorder = Color(0xFFEAEDF0)
val LightBorderStrong = Color(0xFFD9DEE3)
val LightAccent = Color(0xFF3182F6)    // 토스블루
val LightAccentHover = Color(0xFF1B64DA)
val LightGovTint = Color(0xFFE8F3FF)

// ---- Dark role colors ----
val DarkBgApp = Gray950
val DarkBgSurface = Gray900
val DarkBgSurface2 = Color(0xFF131A2A)
val DarkBgMuted = Gray800
val DarkFgStrong = Color(0xFFF3F4F6)
val DarkFgDefault = Gray300
val DarkFgMuted = Gray400
val DarkFgSubtle = Gray500
val DarkFgFaint = Gray600
val DarkBorder = Color(0xFF283449)
val DarkBorderStrong = Gray600
val DarkAccent = Blue500
val DarkAccentHover = Blue400
val DarkGovTint = Color(0xFF16223D)
