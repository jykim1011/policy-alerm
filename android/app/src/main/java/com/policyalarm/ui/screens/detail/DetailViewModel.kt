package com.policyalarm.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.policyalarm.data.model.PolicyDetail
import com.policyalarm.data.repository.PolicyRepository
import com.policyalarm.data.repository.UserRepository
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
                val detail = policyRepo.getPolicyDetail(policyId)
                val bookmarked = userRepo.isBookmarked(policyId)
                policyRepo.markAsRead(policyId)
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

    fun toggleBookmark(policyId: String) {
        viewModelScope.launch {
            val bookmarked = _uiState.value.isBookmarked
            if (bookmarked) userRepo.removeBookmark(policyId)
            else userRepo.saveBookmark(policyId)
            _uiState.value = _uiState.value.copy(isBookmarked = !bookmarked)
        }
    }
}
