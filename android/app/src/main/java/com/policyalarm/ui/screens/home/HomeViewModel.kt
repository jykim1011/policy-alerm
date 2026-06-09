package com.policyalarm.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.policyalarm.data.model.PolicyItem
import com.policyalarm.data.repository.PolicyRepository
import com.policyalarm.data.repository.UserRepository
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
    val bookmarkIds: Set<String> = emptySet(),
) {
    val policies: List<PolicyItem>
        get() = when {
            showBookmarks -> allPolicies.filter { it.id in bookmarkIds }
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
            _uiState.update { it.copy(isLoading = true, error = null, showBookmarks = false, bookmarkIds = emptySet()) }
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
            _uiState.update { it.copy(isLoading = true, showBookmarks = true) }
            try {
                val ids = userRepo.getBookmarkIds().toSet()
                if (_uiState.value.allPolicies.isEmpty()) {
                    val index = repo.getPolicyIndex()
                    _uiState.update { it.copy(allPolicies = index.items, bookmarkIds = ids, isLoading = false) }
                } else {
                    _uiState.update { it.copy(bookmarkIds = ids, isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, showBookmarks = false, error = "북마크 불러오기 실패") }
            }
        }
    }

    fun exitBookmarksMode() {
        _uiState.update { it.copy(showBookmarks = false, bookmarkIds = emptySet()) }
    }
}
