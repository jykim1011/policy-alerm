package com.policyalarm.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.policyalarm.data.model.PolicyDetail
import com.policyalarm.data.model.PolicyItem
import com.policyalarm.data.repository.PolicyRepository
import com.policyalarm.data.repository.UserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val allPolicies: List<PolicyItem> = emptyList(),
    val readIds: Set<String> = emptySet(),
    val selectedCategory: String = "전체",
    val isLoading: Boolean = false,
    val error: String? = null,
    val showBookmarks: Boolean = false,
    val bookmarkPolicies: List<PolicyItem> = emptyList(),
) {
    val policies: List<PolicyItem>
        get() = when {
            showBookmarks -> bookmarkPolicies
            selectedCategory == "전체" -> allPolicies
            else -> allPolicies.filter { it.subcategory == selectedCategory }
        }
}

class HomeViewModel(
    private val repo: PolicyRepository,
    private val userRepo: UserRepository = UserRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        viewModelScope.launch {
            repo.observeReadIds().collect { readIds ->
                _uiState.update { it.copy(readIds = readIds.toSet()) }
            }
        }
        loadPolicies()
    }

    fun loadPolicies() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    showBookmarks = false,
                    bookmarkPolicies = emptyList(),
                )
            }
            try {
                val index = repo.getPolicyIndex()
                _uiState.update { it.copy(allPolicies = index.items, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "불러오기 실패") }
            }
        }
    }

    fun selectCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun loadAndShowBookmarks() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    showBookmarks = true,
                    bookmarkPolicies = emptyList(),
                )
            }
            try {
                val ids = userRepo.getBookmarkIds()
                // 북마크한 정책은 50개짜리 index.json에 없을 수 있으므로 각 상세 JSON을
                // 직접 받아온다. 일부 항목이 실패해도 나머지는 보여준다.
                val policies = coroutineScope {
                    ids.map { id ->
                        async { runCatching { repo.getPolicyDetail(id).toItem() }.getOrNull() }
                    }.awaitAll()
                }.filterNotNull().sortedByDescending { it.publishedAt }
                _uiState.update { it.copy(bookmarkPolicies = policies, isLoading = false) }
            } catch (e: Exception) {
                // showBookmarks를 유지해 북마크 화면에서 에러를 표시한다(무피드백 방지).
                _uiState.update { it.copy(isLoading = false, error = "북마크 불러오기 실패") }
            }
        }
    }

    fun exitBookmarksMode() {
        _uiState.update {
            it.copy(showBookmarks = false, error = null, bookmarkPolicies = emptyList())
        }
    }
}

private fun PolicyDetail.toItem(): PolicyItem = PolicyItem(
    id = id,
    category = category,
    subcategory = subcategory,
    title = title,
    source = source,
    publishedAt = publishedAt,
    summaryPreview = summary?.whatChanged?.take(100)?.plus("...") ?: "",
)
