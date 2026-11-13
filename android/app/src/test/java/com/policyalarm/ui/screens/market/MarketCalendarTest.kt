package com.policyalarm.ui.screens.market

import com.google.gson.Gson
import com.policyalarm.data.model.MarketCalendar
import org.junit.Assert.assertEquals
import org.junit.Test

class MarketCalendarTest {
    private val json = """{"upcoming_days":180,"momentum_days":28,
        "upcoming":[
          {"policy_id":"a","title":"A","published_at":"2026-11-01","sectors":["건설"],"impact":"보통",
           "figures":[{"label":"공급","value":"5만 호"}],"effective_date":"2026-11","date_exact":false},
          {"policy_id":"b","title":"B","published_at":"2026-11-02","sectors":["증권"],"impact":"높음",
           "effective_date":"2026-12-01","date_exact":true},
          {"policy_id":"c","title":"C","published_at":"2026-11-03","impact":"보통","effective_date":"bad"}],
        "momentum":[{"sector":"건설","count":3,"high":1,"positive":2,"negative":1,"mixed":0,
          "policies":[{"policy_id":"a","title":"A","published_at":"2026-11-01","impact":"보통","direction":"긍정"}]}]}"""

    @Test
    fun parsesCalendarAndGroupsByMonth() {
        val cal = Gson().fromJson(json, MarketCalendar::class.java)
        val months = groupByMonth(cal.upcoming!!)
        assertEquals(listOf("2026-11", "2026-12"), months.keys.toList())  // 형식이 틀린 날짜는 빠진다
        assertEquals("b", months["2026-12"]!!.single().policyId)
        assertEquals(true, months["2026-12"]!!.single().dateExact)
        assertEquals(2, cal.momentum!!.single().positive)
        assertEquals("5만 호", cal.upcoming!![0].figures!![0].value)
    }

    @Test
    fun monthLabel_formats() {
        assertEquals("2026년 12월", monthLabel("2026-12"))
        assertEquals("2027년 1월", monthLabel("2027-01"))
    }
}
