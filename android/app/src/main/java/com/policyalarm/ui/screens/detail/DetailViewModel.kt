package com.policyalarm.ui.screens.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.policyalarm.data.model.CommentThread
import com.policyalarm.data.model.PolicyDetail
import com.policyalarm.data.model.PolicyItem
import com.policyalarm.data.model.groupComments
import com.policyalarm.data.repository.CommentRepository
import com.policyalarm.data.repository.PolicyRepository
import com.policyalarm.data.repository.UserRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class DetailUiState(
    val detail: PolicyDetail? = null,
    val isLoading: Boolean = true,
    val isBookmarked: Boolean = false,
    val error: String? = null,
    val commentThreads: List<CommentThread> = emptyList(),
    val commentCount: Int = 0,
    val myUid: String? = null,
    /** 상세 하단 "함께 볼 만한 정책" — 다 읽은 뒤 이탈하지 않고 다음 정책으로 이어지게 한다. */
    val related: List<PolicyItem> = emptyList(),
    val readIds: Set<String> = emptySet(),
)

/**
 * 현재 정책과 함께 보여줄 정책을 고른다. 같은 세부 카테고리 > 같은 대분류 > 나머지 순으로,
 * 같은 순위 안에서는 안 읽은 것을 먼저, 그다음 최신순(입력 순서 유지)으로 [limit]개.
 */
fun pickRelated(
    all: List<PolicyItem>,
    currentId: String,
    category: String,
    subcategory: String,
    readIds: Set<String>,
    limit: Int = RELATED_LIMIT,
): List<PolicyItem> = all
    .filter { it.id != currentId }
    .sortedWith(
        compareByDescending<PolicyItem> {
            when {
                it.subcategory == subcategory -> 2
                it.category == category -> 1
                else -> 0
            }
        }.thenBy { it.id in readIds }
    )
    .take(limit)

const val RELATED_LIMIT = 4

class DetailViewModel(
    private val policyRepo: PolicyRepository,
    private val userRepo: UserRepository = UserRepository(),
    private val commentRepo: CommentRepository = CommentRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState

    fun load(policyId: String) {
        viewModelScope.launch {
            _uiState.value = DetailUiState(isLoading = true)
            try {
                val detail = loadDetailWithRetry(policyId)
                // 북마크/읽음 처리는 부가 정보이므로 실패해도 상세 화면은 보여준다.
                val bookmarked = runCatching { userRepo.isBookmarked(policyId) }.getOrDefault(false)
                runCatching { policyRepo.markAsRead(policyId) }
                _uiState.value = DetailUiState(
                    detail = detail,
                    isBookmarked = bookmarked,
                    isLoading = false,
                )
                loadComments(policyId)
                loadRelated(detail)
            } catch (e: Exception) {
                _uiState.value = DetailUiState(
                    isLoading = false,
                    error = "정책을 불러올 수 없습니다\n[${e.javaClass.simpleName}] ${e.message?.take(80)}",
                )
            }
        }
    }

    /**
     * 상세 JSON을 받아온다. 갓 푸시된 정책은 CDN 반영이 약간 늦을 수 있으므로
     * 짧은 백오프로 몇 번 재시도한다(네트워크 지터 방어).
     */
    private suspend fun loadDetailWithRetry(policyId: String): PolicyDetail {
        val delaysMs = longArrayOf(800, 1600, 2400)
        var lastError: Exception? = null
        for (attempt in 0..delaysMs.size) {
            try {
                return policyRepo.getPolicyDetail(policyId)
            } catch (e: Exception) {
                Log.e("DetailVM", "load attempt $attempt failed — id=[$policyId] ${e.javaClass.simpleName}: ${e.message}")
                lastError = e
                if (attempt < delaysMs.size) delay(delaysMs[attempt])
            }
        }
        val err = lastError ?: IllegalStateException("policy load failed")
        runCatching {
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("policyId", policyId)
                recordException(err)
            }
        }
        throw err
    }

    private fun loadRelated(detail: PolicyDetail) {
        viewModelScope.launch {
            runCatching {
                val readIds = policyRepo.observeReadIds().first().toSet()
                pickRelated(
                    all = policyRepo.getPolicyIndex().items,
                    currentId = detail.id,
                    category = detail.category,
                    subcategory = detail.subcategory,
                    readIds = readIds,
                ) to readIds
            }.onSuccess { (related, readIds) ->
                _uiState.value = _uiState.value.copy(related = related, readIds = readIds)
            }
        }
    }

    fun loadComments(policyId: String) {
        viewModelScope.launch {
            runCatching {
                val flat = commentRepo.getComments(policyId)
                val count = runCatching { commentRepo.count(policyId) }.getOrDefault(flat.size)
                groupComments(flat) to count
            }.onSuccess { (threads, count) ->
                _uiState.value = _uiState.value.copy(
                    commentThreads = threads,
                    commentCount = count,
                    myUid = userRepo.uidOrNull(),
                )
            }
        }
    }

    fun postComment(policyId: String, text: String, parentId: String? = null, mentionNickname: String? = null) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > 1000) return
        viewModelScope.launch {
            runCatching {
                val nickname = userRepo.ensureNickname()
                commentRepo.addComment(policyId, trimmed, nickname, parentId, mentionNickname)
            }.onSuccess { loadComments(policyId) }
        }
    }

    fun deleteComment(policyId: String, commentId: String) {
        viewModelScope.launch {
            runCatching { commentRepo.softDelete(policyId, commentId) }
                .onSuccess { loadComments(policyId) }
        }
    }

    fun toggleBookmark(policyId: String) {
        viewModelScope.launch {
            val bookmarked = _uiState.value.isBookmarked
            // 미로그인/네트워크 등으로 실패해도 크래시 없이 상태를 유지한다.
            val ok = runCatching {
                if (bookmarked) userRepo.removeBookmark(policyId)
                else userRepo.saveBookmark(policyId)
            }.isSuccess
            if (ok) _uiState.value = _uiState.value.copy(isBookmarked = !bookmarked)
        }
    }
}
