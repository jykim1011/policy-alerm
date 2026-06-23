package com.policyalarm

import java.nio.charset.StandardCharsets

/**
 * gen2 Cloud Function 트리거가 한글 등 비ASCII 정책 ID를 UTF-8 바이트→Latin-1로 오독해
 * 만든 모지바케(firebase-functions#1459)를 원래 문자열로 되돌린다.
 *
 * 서버를 고친 뒤 새로 오는 알림은 정상 ID지만, 그 전에 Firestore 알림함에 쌓인 기존
 * 항목은 문서 ID가 깨진 채 남아 있어 탭 시 policies/{id}.json 을 404 로 받는다. 상세 화면
 * 진입 직전에 이 함수로 복원하면 기존 알림도 정상적으로 열린다.
 *
 * round-trip 가드: 모지바케가 아닌 일반 ASCII나 이미 정상인 한글 입력에 적용해도
 * 그대로 반환한다(정상 ID는 ISO-8859-1 인코딩 시 '?'로 깨져 역검증에 실패 → 원본 유지).
 */
fun decodeMojibake(s: String): String {
    if (s.isEmpty()) return s
    return try {
        val restored = String(s.toByteArray(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8)
        if (String(restored.toByteArray(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1) == s) {
            restored
        } else {
            s
        }
    } catch (_: Exception) {
        s
    }
}
