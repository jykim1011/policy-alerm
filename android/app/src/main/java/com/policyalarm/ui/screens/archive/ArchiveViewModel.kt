package com.policyalarm.ui.screens.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.policyalarm.data.model.PolicyItem
import com.policyalarm.data.repository.PolicyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArchiveSection(
    val yearMonth: String,
    val label: String,
    val items: List<PolicyItem>,
)

data class ArchiveUiState(
    val availableYears: List<Int> = emptyList(),
    val selectedYear: Int = 0,
    val sections: List<ArchiveSection> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class ArchiveViewModel(private val repo: PolicyRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ArchiveUiState())
    val uiState: StateFlow<ArchiveUiState> = _uiState

    init { loadYears() }

    private fun loadYears() {
        viewModelScope.launch {
            try {
                val years = repo.getArchiveYears().years.sortedDescending()
                val first = years.firstOrNull() ?: run {
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }
                _uiState.update { it.copy(availableYears = years, selectedYear = first) }
                loadArchive(first)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "불러오기 실패") }
            }
        }
    }

    fun selectYear(year: Int) {
        _uiState.update { it.copy(selectedYear = year, isLoading = true, error = null) }
        viewModelScope.launch { loadArchive(year) }
    }

    private suspend fun loadArchive(year: Int) {
        try {
            val archive = repo.getYearArchive(year)
            _uiState.update { it.copy(sections = groupByMonth(archive.items), isLoading = false) }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, error = "불러오기 실패") }
        }
    }

    private fun groupByMonth(items: List<PolicyItem>): List<ArchiveSection> =
        items
            .groupBy { it.publishedAt.substring(0, 7) }
            .entries
            .sortedByDescending { it.key }
            .map { (ym, group) ->
                val (y, m) = ym.split("-")
                ArchiveSection(
                    yearMonth = ym,
                    label = "${y}년 ${m.trimStart('0')}월 (${group.size}건)",
                    items = group,
                )
            }
}
