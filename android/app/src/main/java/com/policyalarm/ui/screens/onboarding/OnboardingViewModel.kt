package com.policyalarm.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.policyalarm.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

val ALL_CATEGORIES = listOf("청약", "대출", "세금", "재개발", "전월세")

class OnboardingViewModel(
    private val userRepo: UserRepository = UserRepository(),
) : ViewModel() {

    private val _selected = MutableStateFlow(setOf("청약", "대출", "세금"))
    val selected: StateFlow<Set<String>> = _selected

    private val _schedule = MutableStateFlow("both")
    val schedule: StateFlow<String> = _schedule

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done

    fun toggle(category: String) {
        _selected.value = if (category in _selected.value)
            _selected.value - category
        else
            _selected.value + category
    }

    fun setSchedule(value: String) { _schedule.value = value }

    fun confirm() {
        viewModelScope.launch {
            userRepo.updateSubscribedCategories(_selected.value.toList())
            userRepo.updateNotificationSchedule(_schedule.value)
            _done.value = true
        }
    }
}
