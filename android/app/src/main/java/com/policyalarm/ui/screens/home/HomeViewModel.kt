package com.policyalarm.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.policyalarm.data.model.PolicyDetail
import com.policyalarm.data.model.PolicyItem
import com.policyalarm.data.remote.RetrofitClient
import com.policyalarm.data.repository.PolicyRepository
import com.policyalarm.data.repository.UserRepository
import com.policyalarm.data.repository.resolveBookmarks
import com.policyalarm.ui.components.INTEREST_FILTER
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
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
    /** 사용자가 구독한 카테고리(온보딩·설정). 비어 있으면 "관심" 칩을 숨긴다. */
    val interests: Set<String> = emptySet(),
    /** 대시보드의 "안 읽은 것만 보기" 토글. */
    val unreadOnly: Boolean = false,
) {
    /** 홈 상단 대시보드 수치. 날짜는 테스트에서 고정할 수 있게 인자로 받는다. */
    fun dashboard(today: LocalDate = LocalDate.now()): HomeDashboard {
        val week = allPolicies.filter { daysAgo(it.publishedAt, today) in 0..6 }
        return HomeDashboard(
            todayCount = allPolicies.count { daysAgo(it.publishedAt, today) == 0L },
            weekCount = week.size,
            weekReadCount = week.count { it.id in readIds },
            unreadCount = allPolicies.count { it.id !in readIds },
            weekByCategory = week.groupingBy { it.subcategory }.eachCount()
                .entries.sortedByDescending { it.value }
                .map { it.key to it.value },
        )
    }

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
                INTEREST_FILTER -> allPolicies.filter { it.category in interests || it.subcategory in interests }
                else -> allPolicies.filter { it.subcategory == selectedCategory }
            }
            val bySource = if (selectedSource == "전체") byCategory
            else byCategory.filter { it.source == selectedSource }
            return if (unreadOnly) bySource.filter { it.id !in readIds } else bySource
        }
}

data class HomeDashboard(
    val todayCount: Int,
    val weekCount: Int,
    val weekReadCount: Int,
    val unreadCount: Int,
    /** 최근 7일 세부 카테고리별 건수(많은 순). */
    val weekByCategory: List<Pair<String, Int>>,
)

/** 발행일이 [today]로부터 며칠 전인지. 파싱 실패 시 -1(어느 구간에도 안 들어감). */
fun daysAgo(publishedAt: String, today: LocalDate): Long = runCatching {
    ChronoUnit.DAYS.between(OffsetDateTime.parse(publishedAt).toLocalDate(), today)
}.getOrDefault(-1L)

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
        // loadInterests()는 MainScaffold가 홈 탭 진입 때마다 호출한다(첫 진입 포함).
    }

    /**
     * 구독 카테고리를 읽어 "관심" 칩을 채운다. 실패해도(미로그인 등) 칩만 안 보일 뿐이다.
     * 설정 탭에서 구독을 바꾸고 홈으로 돌아올 때도 다시 불러온다.
     */
    fun loadInterests() {
        viewModelScope.launch {
            val interests = runCatching {
                (userRepo.getUserSettings()?.get("subscribed_categories") as? List<*>)
                    ?.filterIsInstance<String>()?.toSet()
            }.getOrNull().orEmpty()
            _uiState.update {
                // 구독을 모두 끈 채 "관심" 필터에 머물면 빈 목록만 남으므로 전체로 되돌린다.
                val category = if (interests.isEmpty() && it.selectedCategory == INTEREST_FILTER) "전체"
                else it.selectedCategory
                it.copy(interests = interests, selectedCategory = category)
            }
        }
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

    fun toggleUnreadOnly() {
        _uiState.update { it.copy(unreadOnly = !it.unreadOnly) }
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
    easyTitle = summary?.easyTitle,
)
