package com.policyalarm.ui.screens.detail

import com.google.gson.Gson
import com.policyalarm.data.model.MarketContext
import com.policyalarm.data.model.MarketSector
import com.policyalarm.data.model.PolicyDetail
import com.policyalarm.data.model.SectorStatsFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketImpactTest {
    private val gson = Gson()

    private fun stats(json: String) = MarketContext(stats = gson.fromJson(json, SectorStatsFile::class.java))

    private val sector = MarketSector(sector = "건설", direction = "긍정", reason = "공급 확대")

    @Test
    fun formatPct_signsAndRounds() {
        assertEquals("+1.2%", formatPct(0.0123))
        assertEquals("-0.5%", formatPct(-0.005))
        assertEquals("0.0%", formatPct(0.0))
    }

    @Test
    fun pickAgg_prefersSameDirectionWhenEnoughSamples() {
        val ctx = stats("""{"min_samples":3,"sectors":{"건설":{"count":5,"avg_d5":0.01,
            "by_direction":{"긍정":{"count":3,"avg_d5":0.02}}}}}""")
        val (agg, same) = pickAgg(sector, ctx)!!
        assertTrue(same)
        assertEquals(0.02, agg.avgD5!!, 1e-9)
    }

    @Test
    fun pickAgg_fallsBackToSectorThenNull() {
        val ctx = stats("""{"min_samples":3,"sectors":{"건설":{"count":4,"avg_d5":0.01,
            "by_direction":{"긍정":{"count":2,"avg_d5":0.02}}}}}""")
        val (agg, same) = pickAgg(sector, ctx)!!
        assertEquals(false, same)
        assertEquals(4, agg.count)

        assertNull(pickAgg(sector, stats("""{"min_samples":3,"sectors":{"건설":{"count":2}}}""")))
        assertNull(pickAgg(sector, MarketContext()))
    }

    @Test
    fun policyDetail_parsesMarketBlock_andToleratesAbsence() {
        val withMarket = gson.fromJson(
            """{"id":"a","category":"금융","subcategory":"금융","title":"t","source":"s","source_url":"u",
               "published_at":"2026-11-01","crawled_at":"","summary":null,
               "market":{"sectors":[{"sector":"증권","direction":"긍정","reason":"r",
               "etfs":[{"code":"102970","name":"KODEX 증권"}],"stocks":[]}],
               "policy_type":"규제 완화","investor_note":"n"}}""",
            PolicyDetail::class.java,
        )
        assertEquals("102970", withMarket.market!!.sectors!![0].etfs!![0].code)
        assertEquals("규제 완화", withMarket.market!!.policyType)
        // impact 가 없으면(중요도 판정 전 데이터) 카드를 숨긴다
        assertEquals(false, withMarket.market!!.isShown)

        val without = gson.fromJson(
            """{"id":"a","category":"c","subcategory":"c","title":"t","source":"s","source_url":"u",
               "published_at":"2026-11-01","crawled_at":"","summary":null}""",
            PolicyDetail::class.java,
        )
        assertNull(without.market)
    }

    @Test
    fun isShown_onlyForHighOrMedium() {
        val base = com.policyalarm.data.model.PolicyMarket(sectors = listOf(sector), impact = "보통")
        assertTrue(base.isShown)
        assertTrue(base.copy(impact = "높음").isShown)
        assertEquals(false, base.copy(impact = "낮음").isShown)
        assertEquals(false, base.copy(sectors = emptyList()).isShown)
    }
}
