package com.policyalarm.screens

import com.policyalarm.data.model.PolicyIndex
import com.policyalarm.data.model.PolicyItem
import com.policyalarm.data.repository.PolicyRepository
import com.policyalarm.data.repository.UserRepository
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
    // 실제 UserRepository는 생성 시 Firebase(android.os.Process)에 접근해 JVM 테스트에서
    // 죽으므로 mock을 주입한다.
    private val mockUserRepo = mockk<UserRepository>(relaxed = true)

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

        val vm = HomeViewModel(mockRepo, mockUserRepo)
        assertEquals(1, vm.uiState.value.policies.size)
        assertEquals("id-1", vm.uiState.value.policies[0].id)
    }
}
