package com.policyalarm.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** 알림 탭에 표시되는 한 건. Firestore users/{uid}/notifications/{policyId} 문서 매핑. */
data class NotificationItem(
    val policyId: String,
    val title: String,
    val category: String,
    val receivedAt: Long,
    val isRead: Boolean,
)

/**
 * 알림 기록은 Cloud Function이 발송 시 Firestore에 써 두는 것을 단일 소스로 사용한다.
 * 앱 상태(포그라운드/백그라운드/종료)나 탭 여부와 무관하게 항상 정확하다.
 */
class NotificationRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private fun notifications() = db.collection("users")
        .document(auth.currentUser?.uid ?: error("로그인 필요"))
        .collection("notifications")

    fun observeNotifications(): Flow<List<NotificationItem>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList()); close(); return@callbackFlow
        }
        val reg = db.collection("users").document(uid).collection("notifications")
            .orderBy("received_at", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    trySend(
                        snap.documents.map { d ->
                            NotificationItem(
                                policyId = d.id,
                                title = d.getString("title") ?: "",
                                category = d.getString("category") ?: "",
                                receivedAt = d.getTimestamp("received_at")?.toDate()?.time
                                    ?: System.currentTimeMillis(),
                                isRead = d.getBoolean("read") ?: false,
                            )
                        }
                    )
                }
            }
        awaitClose { reg.remove() }
    }

    fun observeUnreadCount(): Flow<Int> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(0); close(); return@callbackFlow
        }
        val reg = db.collection("users").document(uid).collection("notifications")
            .whereEqualTo("read", false)
            .addSnapshotListener { snap, _ -> trySend(snap?.size() ?: 0) }
        awaitClose { reg.remove() }
    }

    suspend fun markRead(policyId: String) {
        runCatching { notifications().document(policyId).update("read", true).await() }
    }

    /** "모두 읽음": 알림함을 비운다(목록의 모든 내용이 사라짐). */
    suspend fun clearAll() {
        val uid = auth.currentUser?.uid ?: return
        runCatching {
            val snap = db.collection("users").document(uid).collection("notifications").get().await()
            if (snap.isEmpty) return
            val batch = db.batch()
            snap.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }
}
