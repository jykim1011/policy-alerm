package com.policyalarm.screens

import com.policyalarm.data.model.PolicyItem
import com.policyalarm.ui.screens.home.HomeUiState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HomeDashboardTest {

    private fun item(id: String, sub: String, date: String) =
        PolicyItem(id, "부동산", sub, "t", "부처", "${date}T09:00:00+09:00", "")

    private val today = LocalDate.parse("2026-10-08")

    @Test
    fun `오늘·이번 주 건수와 읽음 진행률, 분야별 건수를 센다`() {
        val state = HomeUiState(
            allPolicies = listOf(
                item("a", "청약", "2026-10-08"),
                item("b", "청약", "2026-10-07"),
                item("c", "대출", "2026-10-02"),
                item("old", "대출", "2026-09-20"),
            ),
            readIds = setOf("b", "old"),
        )
        val d = state.dashboard(today)
        assertEquals(1, d.todayCount)
        assertEquals(3, d.weekCount)
        assertEquals(1, d.weekReadCount)
        assertEquals(2, d.unreadCount)
        assertEquals(listOf("청약" to 2, "대출" to 1), d.weekByCategory)
    }

    @Test
    fun `안 읽은 것만 보기는 읽은 정책을 목록에서 뺀다`() {
        val state = HomeUiState(
            allPolicies = listOf(item("a", "청약", "2026-10-08"), item("b", "청약", "2026-10-07")),
            readIds = setOf("a"),
            unreadOnly = true,
        )
        assertEquals(listOf("b"), state.policies.map { it.id })
    }
}
