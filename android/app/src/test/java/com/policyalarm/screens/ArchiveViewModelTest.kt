package com.policyalarm.screens

import com.policyalarm.data.model.ArchiveYears
import com.policyalarm.data.model.PolicyItem
import com.policyalarm.data.model.YearArchive
import com.policyalarm.data.repository.PolicyRepository
import com.policyalarm.ui.screens.archive.ArchiveViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val mockRepo = mockk<PolicyRepository>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { mockRepo.observeReadIds() } returns flowOf(emptyList())
    }

    @After
    fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `init은 연도 목록과 첫 연도 아카이브를 로드한다`() = runTest {
        coEvery { mockRepo.getArchiveYears() } returns ArchiveYears(listOf(2026), "2026-06-20")
        coEvery { mockRepo.getYearArchive(2026) } returns YearArchive(
            year = 2026, total = 2, updatedAt = "2026-06-20",
            items = listOf(
                PolicyItem("id-1", "부동산", "청약", "청약 개편", "국토교통부", "2026-06-19T09:00:00+09:00", ""),
                PolicyItem("id-2", "부동산", "대출", "대출 규제", "금융위원회", "2026-05-15T09:00:00+09:00", ""),
            )
        )

        val vm = ArchiveViewModel(mockRepo)

        assertEquals(listOf(2026), vm.uiState.value.availableYears)
        assertEquals(2026, vm.uiState.value.selectedYear)
        assertEquals(2, vm.uiState.value.sections.size)
        assertEquals("2026년 6월 (1건)", vm.uiState.value.sections[0].label)
        assertEquals("2026년 5월 (1건)", vm.uiState.value.sections[1].label)
    }

    @Test
    fun `selectYear는 해당 연도 아카이브로 섹션을 교체한다`() = runTest {
        coEvery { mockRepo.getArchiveYears() } returns ArchiveYears(listOf(2026, 2025), "2026-06-20")
        coEvery { mockRepo.getYearArchive(2026) } returns YearArchive(
            2026, 1, "2026-06-20",
            listOf(PolicyItem("id-1", "부동산", "청약", "청약 개편", "국토교통부", "2026-06-19T09:00:00+09:00", ""))
        )
        coEvery { mockRepo.getYearArchive(2025) } returns YearArchive(
            2025, 1, "2025-12-31",
            listOf(PolicyItem("id-2", "고용", "고용", "취업 지원", "고용노동부", "2025-11-01T09:00:00+09:00", ""))
        )

        val vm = ArchiveViewModel(mockRepo)
        vm.selectYear(2025)

        assertEquals(2025, vm.uiState.value.selectedYear)
        assertEquals(1, vm.uiState.value.sections.size)
        assertEquals("2025년 11월 (1건)", vm.uiState.value.sections[0].label)
    }
}
