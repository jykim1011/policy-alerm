package com.policyalarm.data.repository

import com.policyalarm.data.local.ReadPolicyDao
import com.policyalarm.data.local.ReadPolicyEntity
import com.policyalarm.data.model.ArchiveYears
import com.policyalarm.data.model.PolicyDetail
import com.policyalarm.data.model.PolicyIndex
import com.policyalarm.data.model.YearArchive
import com.policyalarm.data.remote.PolicyApiService
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

    fun observeReadIds(): Flow<List<String>> = readPolicyDao.observeReadIds()

    suspend fun markAsRead(policyId: String) {
        readPolicyDao.markAsRead(ReadPolicyEntity(policyId))
    }
}
