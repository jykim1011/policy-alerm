package com.policyalarm.screens

import com.policyalarm.data.model.PolicyItem
import com.policyalarm.ui.screens.detail.pickRelated
import org.junit.Assert.assertEquals
import org.junit.Test

class RelatedPoliciesTest {

    private fun item(id: String, category: String, subcategory: String) =
        PolicyItem(id, category, subcategory, "제목 $id", "부처", "2026-10-01T09:00:00+09:00", "")

    @Test
    fun `같은 세부 카테고리 - 같은 대분류 - 나머지 순으로 고르고 현재 정책은 뺀다`() {
        val all = listOf(
            item("other", "고용", "고용"),
            item("cat", "부동산", "대출"),
            item("current", "부동산", "청약"),
            item("sub", "부동산", "청약"),
        )
        val result = pickRelated(all, "current", "부동산", "청약", readIds = emptySet())
        assertEquals(listOf("sub", "cat", "other"), result.map { it.id })
    }

    @Test
    fun `같은 순위 안에서는 안 읽은 정책을 먼저 보이고 limit 개수만 반환한다`() {
        val all = listOf(
            item("read", "부동산", "청약"),
            item("unread1", "부동산", "청약"),
            item("unread2", "부동산", "청약"),
        )
        val result = pickRelated(all, "x", "부동산", "청약", readIds = setOf("read"), limit = 2)
        assertEquals(listOf("unread1", "unread2"), result.map { it.id })
    }
}
