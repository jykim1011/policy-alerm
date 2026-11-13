package com.policyalarm.data.repository

import com.policyalarm.data.local.ReadPolicyDao
import com.policyalarm.data.local.ReadPolicyEntity
import com.policyalarm.data.model.ArchiveYears
import com.policyalarm.data.model.MarketContext
import com.policyalarm.data.model.SectorInfo
import com.policyalarm.data.model.PolicyDetail
import com.policyalarm.data.model.PolicyIndex
import com.policyalarm.data.model.YearArchive
import com.policyalarm.data.remote.PolicyApiService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

class PolicyRepository(
    private val api: PolicyApiService,
    private val readPolicyDao: ReadPolicyDao,
) {
    suspend fun getPolicyIndex(): PolicyIndex = api.getPolicyIndex()

    suspend fun getCategoryIndex(subcategory: String): PolicyIndex =
        api.getCategoryIndex(subcategory)

    suspend fun getPolicyDetail(id: String): PolicyDetail = api.getPolicyDetail(id)

    suspend fun getArchiveYears(): ArchiveYears = api.getArchiveYears()
    suspend fun getYearArchive(year: Int): YearArchive = api.getYearArchive(year)

    /**
     * 시장 카드용 부가 데이터. 세 파일 중 일부가 없거나(키 미설정으로 아직 생성 전) 실패해도
     * 나머지로 카드를 그린다.
     */
    suspend fun getMarketContext(detail: PolicyDetail): MarketContext = coroutineScope {
        val stats = async { runCatching { api.getSectorStats() }.getOrNull() }
        val reactions = async { runCatching { api.getReactions() }.getOrNull() }
        val disclosures = async { runCatching { api.getDisclosures() }.getOrNull() }
        val since = detail.publishedAt.take(10)
        val codes = detail.market?.sectors.orEmpty().flatMap { it.stocks.orEmpty() }.map { it.code }.toSet()
        MarketContext(
            stats = stats.await(),
            reactions = reactions.await()?.items.orEmpty().values
                .filter { it.policyId == detail.id }
                .associateBy { it.sector },
            disclosures = disclosures.await()?.stocks.orEmpty()
                .filterKeys { it in codes }
                .mapValues { (_, list) -> list.filter { it.date >= since }.sortedByDescending { it.date } }
                .filterValues { it.isNotEmpty() },
        )
    }

    suspend fun getSectors(): List<SectorInfo> = api.getSectors().sectors.orEmpty()

    fun observeReadIds(): Flow<List<String>> = readPolicyDao.observeReadIds()

    suspend fun markAsRead(policyId: String) {
        readPolicyDao.markAsRead(ReadPolicyEntity(policyId))
    }

    suspend fun markAllAsRead(policyIds: List<String>) {
        if (policyIds.isEmpty()) return
        readPolicyDao.markAllAsRead(policyIds.map { ReadPolicyEntity(it) })
    }

    suspend fun unmarkRead(policyIds: List<String>) {
        if (policyIds.isEmpty()) return
        readPolicyDao.unmarkRead(policyIds)
    }
}
