package com.policyalarm.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "read_policies")
data class ReadPolicyEntity(
    @PrimaryKey val policyId: String,
    val readAt: Long = System.currentTimeMillis(),
)
