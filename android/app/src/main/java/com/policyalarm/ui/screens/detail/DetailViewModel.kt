package com.policyalarm.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.policyalarm.data.model.PolicyDetail
import com.policyalarm.data.repository.PolicyRepository
import com.policyalarm.data.repository.UserRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DetailUiState(
    val detail: PolicyDetail? = null,
    val isLoading: Boolean = true,
    val isBookmarked: Boolean = false,
    val error: String? = null,
)

class DetailViewModel(
    private val policyRepo: PolicyRepository,
    private val userRepo: UserRepository = UserRepository(),
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
            } catch (e: Exception) {
                _uiState.value = DetailUiState(
                    isLoading = false,
                    error = "정책을 불러올 수 없습니다",
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
                lastError = e
                if (attempt < delaysMs.size) delay(delaysMs[attempt])
            }
        }
        throw lastError ?: IllegalStateException("policy load failed")
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
