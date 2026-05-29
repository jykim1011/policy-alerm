package com.policyalarm.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadPolicyDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markAsRead(entity: ReadPolicyEntity)

    @Query("SELECT policyId FROM read_policies")
    fun observeReadIds(): Flow<List<String>>

    @Query("SELECT COUNT(*) > 0 FROM read_policies WHERE policyId = :policyId")
    suspend fun isRead(policyId: String): Boolean
}
