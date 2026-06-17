package com.policyalarm.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_history")
data class NotificationHistoryEntity(
    @PrimaryKey val policyId: String,
    val title: String,
    val category: String,
    val receivedAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
)
