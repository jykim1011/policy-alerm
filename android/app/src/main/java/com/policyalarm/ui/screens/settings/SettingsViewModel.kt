package com.policyalarm.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.policyalarm.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val subscribedCategories: Set<String> = emptySet(),
    val notificationSchedule: String = "both",
    val userName: String = "사용자",
    val userEmail: String = "",
    val bookmarkCount: Int = 0,
    val isLoading: Boolean = true,
)

class SettingsViewModel(
    private val userRepo: UserRepository = UserRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init { loadSettings() }

    private fun loadSettings() {
        viewModelScope.launch {
            val settings = userRepo.getUserSettings()
            @Suppress("UNCHECKED_CAST")
            var categories = (settings?.get("subscribed_categories") as? List<String>)
                ?.toSet() ?: emptySet()
            val schedule = settings?.get("notification_schedule") as? String ?: "both"

            // 부동산 서브카테고리를 구독 중인데 상위 "부동산"이 없는 기존 유저를 조용히 마이그레이션
            val realEstateSubs = setOf("청약", "대출", "세금", "재개발", "전월세")
            if (categories.any { it in realEstateSubs } && "부동산" !in categories) {
                categories = categories + "부동산"
                runCatching { userRepo.updateSubscribedCategories(categories.toList()) }
            }

            _uiState.value = _uiState.value.copy(
                subscribedCategories = categories,
                notificationSchedule = schedule,
                userName = userRepo.displayName() ?: "사용자",
                userEmail = userRepo.email() ?: "",
                isLoading = false,
            )
            runCatching { userRepo.countBookmarks() }
                .onSuccess { count -> _uiState.value = _uiState.value.copy(bookmarkCount = count) }
        }
    }

    /** Toggle a category and persist immediately (live save). */
    fun toggleCategory(category: String) {
        val current = _uiState.value.subscribedCategories
        val next = if (category in current) current - category else current + category
        _uiState.value = _uiState.value.copy(subscribedCategories = next)
        viewModelScope.launch {
            runCatching { userRepo.updateSubscribedCategories(next.toList()) }
        }
    }

    fun setSchedule(schedule: String) {
        _uiState.value = _uiState.value.copy(notificationSchedule = schedule)
        viewModelScope.launch {
            runCatching { userRepo.updateNotificationSchedule(schedule) }
        }
    }

    fun refreshBookmarkCount() {
        viewModelScope.launch {
            runCatching { userRepo.countBookmarks() }
                .onSuccess { count -> _uiState.value = _uiState.value.copy(bookmarkCount = count) }
        }
    }

    fun logout() = userRepo.logout()
}
