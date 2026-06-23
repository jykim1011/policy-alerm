package com.policyalarm.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.policyalarm.data.remote.PolicyApiService
import com.policyalarm.data.remote.RetrofitClient
import com.policyalarm.data.repository.UserRepository
import com.policyalarm.data.repository.resolveBookmarks
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
    private val policyApi: PolicyApiService = RetrofitClient.policyApi,
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
            // 북마크 개수는 화면 진입 시 LaunchedEffect 가 refreshBookmarkCount() 로 갱신한다.
            // 여기서 또 세면 화면 첫 진입 때 resolveBookmarks(상세 N건 fetch)가 중복 실행된다.
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
            // 북마크 목록 화면(HomeViewModel)과 같은 resolveBookmarks 로 세어, 정책이 사라진
            // 고아 북마크를 빼고 정리한다 → 목록과 개수가 항상 일치한다.
            runCatching { resolveBookmarks(policyApi, userRepo).size }
                .onSuccess { count -> _uiState.value = _uiState.value.copy(bookmarkCount = count) }
        }
    }

    fun logout() = userRepo.logout()
}
