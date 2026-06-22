package com.policyalarm.data.repository

import android.util.Log
import com.policyalarm.data.model.PolicyDetail
import com.policyalarm.data.remote.PolicyApiService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import retrofit2.HttpException

/**
 * 북마크 목록과 설정 화면의 개수가 어긋나는 문제를 한 정의로 통일한다.
 *
 * 기존엔 목록 화면은 불러오기 실패한 항목을 조용히 버리고(getOrNull),
 * 설정 화면은 Firestore 북마크 문서 수를 그대로 셌다. 그래서 정책 JSON이
 * 사라진(404) 고아 북마크가 있으면 목록=1, 설정=2 처럼 불일치가 났다.
 *
 * 각 북마크의 상세 JSON을 받아보고:
 *  - 성공: 유효한 북마크
 *  - HTTP 404: 정책이 사라진 고아 → Firestore 에서 삭제(prune)
 *  - 기타 오류(네트워크 등): 이번엔 세지 않되 삭제도 하지 않음(다음 기회에 복구)
 *
 * 반환된 목록의 크기가 곧 "실제 유효한 북마크 수"이며, 목록과 개수가 항상 일치한다.
 */
suspend fun resolveBookmarks(
    api: PolicyApiService,
    userRepo: UserRepository,
): List<PolicyDetail> = coroutineScope {
    userRepo.getBookmarkIds().map { id ->
        async {
            try {
                api.getPolicyDetail(id)
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    // 정책 JSON이 더 이상 없는 고아 북마크 — 제거해 목록/개수를 정리한다.
                    Log.w("BookmarkResolver", "Pruning orphan bookmark (404): id=$id")
                    runCatching { userRepo.removeBookmark(id) }
                }
                null
            } catch (e: Exception) {
                // 일시적 네트워크 오류 등 — 삭제하지 않고 이번 집계에서만 제외한다.
                null
            }
        }
    }.awaitAll().filterNotNull()
}
