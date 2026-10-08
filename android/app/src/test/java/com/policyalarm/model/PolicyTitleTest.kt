package com.policyalarm.model

import com.policyalarm.data.model.PolicyItem
import com.policyalarm.data.model.cleanTitle
import org.junit.Assert.assertEquals
import org.junit.Test

class PolicyTitleTest {

    @Test
    fun `머리표를 여러 개 떼어낸다`() {
        assertEquals(
            "조달청은 공공주택 CM용역 벌점 강화",
            cleanTitle("[사실은 이렇습니다] [보도설명자료] 조달청은 공공주택 CM용역 벌점 강화"),
        )
        assertEquals("미래대응기금 설명", cleanTitle("[사실은 이렇습니다] (보도설명) 미래대응기금 설명"))
    }

    @Test
    fun `머리표뿐이면 원제목을 그대로 둔다`() {
        assertEquals("[보도자료]", cleanTitle("[보도자료]"))
    }

    @Test
    fun `쉬운 제목이 있으면 그것을, 없으면 다듬은 원제목을 보여준다`() {
        val base = PolicyItem("id", "부동산", "청약", "[참고] 청약 개편", "국토교통부", "2026-10-01T09:00:00+09:00", "")
        assertEquals("청약 개편", base.displayTitle)
        assertEquals("청약 문턱이 낮아져요", base.copy(easyTitle = "청약 문턱이 낮아져요").displayTitle)
        assertEquals("청약 개편", base.copy(easyTitle = " ").displayTitle)
    }
}
