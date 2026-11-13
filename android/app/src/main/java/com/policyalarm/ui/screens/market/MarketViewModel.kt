package com.policyalarm.ui.screens.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.policyalarm.data.model.MarketCalendar
import com.policyalarm.data.remote.PolicyApiService
import com.policyalarm.data.remote.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MarketUiState(
    val calendar: MarketCalendar? = null,
    val isLoading: Boolean = true,
    val error: Boolean = false,
)

class MarketViewModel(
    private val api: PolicyApiService = RetrofitClient.policyApi,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MarketUiState())
    val uiState: StateFlow<MarketUiState> = _uiState

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = false)
            runCatching { api.getMarketCalendar() }
                .onSuccess { _uiState.value = MarketUiState(calendar = it, isLoading = false) }
                .onFailure { _uiState.value = MarketUiState(isLoading = false, error = true) }
        }
    }
}

/** 시행일을 달별로 묶는다. key = "YYYY-MM"(입력 순서 = 날짜순 유지). */
fun groupByMonth(entries: List<com.policyalarm.data.model.CalendarEntry>) =
    entries.groupBy { it.effectiveDate.orEmpty().take(7) }.filterKeys { it.length == 7 }

/** "2026-12" → "2026년 12월" */
fun monthLabel(ym: String): String = "${ym.take(4)}년 ${ym.substring(5, 7).toInt()}월"
