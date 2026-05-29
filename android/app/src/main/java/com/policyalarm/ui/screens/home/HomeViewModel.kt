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
    val policies: List<PolicyItem> = emptyList(),
    val readIds: Set<String> = emptySet(),
    val selectedCategory: String = "전체",
    val isLoading: Boolean = false,
    val error: String? = null,
)

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
                _uiState.update { it.copy(policies = index.items, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "불러오기 실패") }
            }
        }
    }

    fun selectCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
        if (category == "전체") {
            loadPolicies()
        } else {
            viewModelScope.launch {
                try {
                    val index = repo.getCategoryIndex(category)
                    _uiState.update { it.copy(policies = index.items) }
                } catch (_: Exception) {}
            }
        }
    }
}
