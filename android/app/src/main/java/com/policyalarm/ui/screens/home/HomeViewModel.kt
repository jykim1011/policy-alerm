package com.policyalarm.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.policyalarm.data.model.PolicyDetail
import com.policyalarm.data.model.PolicyItem
import com.policyalarm.data.remote.RetrofitClient
import com.policyalarm.data.repository.PolicyRepository
import com.policyalarm.data.repository.UserRepository
import com.policyalarm.data.repository.resolveBookmarks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val allPolicies: List<PolicyItem> = emptyList(),
    val readIds: Set<String> = emptySet(),
    val selectedCategory: String = "전체",
    val selectedSource: String = "전체",
    val isLoading: Boolean = false,
    val error: String? = null,
    val showBookmarks: Boolean = false,
    val bookmarkPolicies: List<PolicyItem> = emptyList(),
) {
    /** 현재 불러온 정책들의 주관부처 목록(빈도 내림차순). 필터 드롭다운에 사용. */
    val sources: List<String>
        get() = allPolicies
            .mapNotNull { it.source?.takeIf { s -> s.isNotBlank() } }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { it.key }

    val policies: List<PolicyItem>
        get() {
            if (showBookmarks) return bookmarkPolicies
            val byCategory = when (selectedCategory) {
                "전체" -> allPolicies
                "부동산" -> allPolicies.filter { it.category == "부동산" }
                else -> allPolicies.filter { it.subcategory == selectedCategory }
            }
            return if (selectedSource == "전체") byCategory
            else byCategory.filter { it.source == selectedSource }
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

    fun selectSource(source: String) {
        _uiState.update { it.copy(selectedSource = source) }
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
                // 북마크한 정책은 50개짜리 index.json에 없을 수 있으므로 각 상세 JSON을
                // 직접 받아온다. 정책이 사라진(404) 고아 북마크는 제거하고, 나머지를 보여준다.
                // (설정 화면 개수도 같은 resolveBookmarks 로 세어 목록과 항상 일치한다.)
                val policies = resolveBookmarks(RetrofitClient.policyApi, userRepo)
                    .map { it.toItem() }
                    .sortedByDescending { it.publishedAt }
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
