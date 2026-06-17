package com.policyalarm.repository

import com.policyalarm.data.local.ReadPolicyDao
import com.policyalarm.data.model.PolicyIndex
import com.policyalarm.data.model.PolicyItem
import com.policyalarm.data.remote.PolicyApiService
import com.policyalarm.data.repository.PolicyRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PolicyRepositoryTest {

    private val mockApi = mockk<PolicyApiService>()
    private val mockDao = mockk<ReadPolicyDao>(relaxed = true)
    private val repo = PolicyRepository(mockApi, mockDao)

    private fun makeItem(id: String) = PolicyItem(
        id = id, category = "부동산", subcategory = "청약",
        title = "테스트 정책", source = "국토교통부",
        publishedAt = "2026-05-29T09:00:00+09:00",
        summaryPreview = "요약..."
    )

    @Test
    fun `getPolicyIndex returns items from API`() = runTest {
        val index = PolicyIndex("2026-05-29", 1, listOf(makeItem("id-001")))
        coEvery { mockApi.getPolicyIndex() } returns index

        val result = repo.getPolicyIndex()
        assertEquals(1, result.items.size)
        assertEquals("id-001", result.items[0].id)
    }

    @Test
    fun `markAsRead calls dao`() = runTest {
        repo.markAsRead("id-001")
        // readAt은 호출 시각 기반이라 동등 비교가 불안정하므로 policyId만 검증한다.
        coVerify { mockDao.markAsRead(match { it.policyId == "id-001" }) }
    }
}
