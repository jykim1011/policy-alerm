package com.policyalarm.screens

import com.policyalarm.data.model.PolicyIndex
import com.policyalarm.data.model.PolicyItem
import com.policyalarm.data.repository.PolicyRepository
import com.policyalarm.ui.screens.home.HomeViewModel
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
class HomeViewModelTest {

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
    fun `loadPolicies populates items`() = runTest {
        val items = listOf(
            PolicyItem(
                "id-1", "부동산", "청약", "청약 개편", "국토교통부",
                "2026-05-29T09:00:00+09:00", "요약..."
            )
        )
        coEvery { mockRepo.getPolicyIndex() } returns PolicyIndex("2026-05-29", 1, items)

        val vm = HomeViewModel(mockRepo)
        assertEquals(1, vm.uiState.value.policies.size)
        assertEquals("id-1", vm.uiState.value.policies[0].id)
    }
}
