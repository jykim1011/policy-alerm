package com.policyalarm.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.policyalarm.data.repository.UserRepository
import com.policyalarm.ui.screens.onboarding.ALL_CATEGORIES
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val subscribedCategories: Set<String> = emptySet(),
    val notificationSchedule: String = "both",
    val isLoading: Boolean = true,
    val isSaved: Boolean = false,
)

class SettingsViewModel(
    private val userRepo: UserRepository = UserRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init { loadSettings() }

    private fun loadSettings() {
        viewModelScope.launch {
            val settings = userRepo.getUserSettings() ?: return@launch
            @Suppress("UNCHECKED_CAST")
            val categories = (settings["subscribed_categories"] as? List<String>)
                ?.toSet() ?: emptySet()
            val schedule = settings["notification_schedule"] as? String ?: "both"
            _uiState.value = SettingsUiState(
                subscribedCategories = categories,
                notificationSchedule = schedule,
                isLoading = false,
            )
        }
    }

    fun toggleCategory(category: String) {
        val current = _uiState.value.subscribedCategories
        _uiState.value = _uiState.value.copy(
            subscribedCategories = if (category in current) current - category else current + category
        )
    }

    fun setSchedule(schedule: String) {
        _uiState.value = _uiState.value.copy(notificationSchedule = schedule)
    }

    fun save() {
        viewModelScope.launch {
            userRepo.updateSubscribedCategories(_uiState.value.subscribedCategories.toList())
            userRepo.updateNotificationSchedule(_uiState.value.notificationSchedule)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }
}
