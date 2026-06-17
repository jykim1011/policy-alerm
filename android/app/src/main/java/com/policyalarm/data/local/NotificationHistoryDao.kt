package com.policyalarm.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: NotificationHistoryEntity)

    // 백그라운드/종료 상태에서 시스템이 알림 표시 후 탭 시 MainActivity에서 호출
    // 이미 onMessageReceived로 저장된 경우 덮어쓰지 않도록 IGNORE
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: NotificationHistoryEntity)

    @Query("SELECT * FROM notification_history ORDER BY receivedAt DESC")
    fun observeAll(): Flow<List<NotificationHistoryEntity>>

    @Query("SELECT COUNT(*) FROM notification_history WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Query("UPDATE notification_history SET isRead = 1 WHERE policyId = :policyId")
    suspend fun markRead(policyId: String)

    @Query("DELETE FROM notification_history")
    suspend fun deleteAll()

    @Query("DELETE FROM notification_history WHERE receivedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
