package com.policyalarm.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.policyalarm.data.model.PolicyItem
import com.policyalarm.data.repository.PolicyRepository
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
) {
    /** 화면에 표시할 목록: 선택된 카테고리(subcategory)로 로컬 필터링한다. */
    val policies: List<PolicyItem>
        get() = if (selectedCategory == "전체") allPolicies
        else allPolicies.filter { it.subcategory == selectedCategory }
}

class HomeViewModel(
    private val repo: PolicyRepository,
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
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val index = repo.getPolicyIndex()
                _uiState.update { it.copy(allPolicies = index.items, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "불러오기 실패") }
            }
        }
    }

    fun selectCategory(category: String) {
        // 메인 인덱스를 로컬 필터링하므로 추가 네트워크 호출이 없다.
        _uiState.update { it.copy(selectedCategory = category) }
    }
}
