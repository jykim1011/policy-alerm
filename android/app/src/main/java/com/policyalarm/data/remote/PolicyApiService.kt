package com.policyalarm.data.remote

import com.policyalarm.data.model.ArchiveYears
import com.policyalarm.data.model.DisclosuresFile
import com.policyalarm.data.model.MarketCalendar
import com.policyalarm.data.model.ReactionsFile
import com.policyalarm.data.model.SectorStatsFile
import com.policyalarm.data.model.SectorsFile
import com.policyalarm.data.model.PolicyDetail
import com.policyalarm.data.model.PolicyIndex
import com.policyalarm.data.model.YearArchive
import retrofit2.http.GET
import retrofit2.http.Path

interface PolicyApiService {
    @GET("policies/index.json")
    suspend fun getPolicyIndex(): PolicyIndex

    @GET("policies/{id}.json")
    suspend fun getPolicyDetail(@Path("id") id: String): PolicyDetail

    @GET("categories/{subcategory}/index.json")
    suspend fun getCategoryIndex(@Path("subcategory") subcategory: String): PolicyIndex

    @GET("archive/index.json")
    suspend fun getArchiveYears(): ArchiveYears

    @GET("archive/{year}.json")
    suspend fun getYearArchive(@Path("year") year: Int): YearArchive

    // 시장 부가 데이터(pipeline/market_stats.py가 매일 갱신). 없으면 404 → 호출부에서 무시.
    @GET("market/sector_stats.json")
    suspend fun getSectorStats(): SectorStatsFile

    @GET("market/reactions.json")
    suspend fun getReactions(): ReactionsFile

    @GET("market/disclosures.json")
    suspend fun getDisclosures(): DisclosuresFile

    @GET("market/sectors.json")
    suspend fun getSectors(): SectorsFile

    @GET("market/calendar.json")
    suspend fun getMarketCalendar(): MarketCalendar
}
