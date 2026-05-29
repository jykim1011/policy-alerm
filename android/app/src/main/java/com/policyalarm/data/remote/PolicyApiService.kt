package com.policyalarm.data.remote

import com.policyalarm.data.model.PolicyDetail
import com.policyalarm.data.model.PolicyIndex
import retrofit2.http.GET
import retrofit2.http.Path

interface PolicyApiService {
    @GET("policies/index.json")
    suspend fun getPolicyIndex(): PolicyIndex

    @GET("policies/{id}.json")
    suspend fun getPolicyDetail(@Path("id") id: String): PolicyDetail

    @GET("categories/{subcategory}/index.json")
    suspend fun getCategoryIndex(@Path("subcategory") subcategory: String): PolicyIndex
}
