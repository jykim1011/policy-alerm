package com.policyalarm.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private val uid get() = auth.currentUser?.uid ?: error("로그인 필요")

    suspend fun saveUserSettings(
        fcmToken: String,
        subscribedCategories: List<String>,
        notificationSchedule: String,
    ) {
        db.collection("users").document(uid).set(
            mapOf(
                "fcm_token" to fcmToken,
                "subscribed_categories" to subscribedCategories,
                "notification_schedule" to notificationSchedule,
            )
        ).await()
    }

    suspend fun getUserSettings(): Map<String, Any>? =
        db.collection("users").document(uid).get().await().data

    suspend fun getNickname(): String? =
        db.collection("users").document(uid).get().await().getString("nickname")

    suspend fun updateNickname(nickname: String) {
        db.collection("users").document(uid)
            .set(mapOf("nickname" to nickname), SetOptions.merge()).await()
    }

    /**
     * 닉네임이 없으면 자동 생성해 저장하고 반환한다. 이미 있으면 그대로 반환.
     * 댓글 작성 등 표시 이름이 필요한 시점에 호출해, "익명" 폴백 없이 항상 채워진 값을 보장한다.
     */
    suspend fun ensureNickname(): String {
        getNickname()?.takeIf { it.isNotBlank() }?.let { return it }
        val generated = com.policyalarm.util.NicknameGenerator.generate()
        updateNickname(generated)
        return generated
    }

    suspend fun updateFcmToken(token: String) {
        // .update()는 문서가 없으면 NOT_FOUND 예외를 던진다. 신규 사용자 가입 직후 또는
        // 토큰 갱신이 Firestore 문서 생성보다 먼저 도달하는 경쟁 조건을 방어하기 위해
        // merge 옵션으로 .set()을 사용한다.
        db.collection("users").document(uid)
            .set(mapOf("fcm_token" to token), SetOptions.merge()).await()
    }

    suspend fun updateSubscribedCategories(categories: List<String>) {
        db.collection("users").document(uid)
            .update("subscribed_categories", categories).await()
    }

    suspend fun updateNotificationSchedule(schedule: String) {
        db.collection("users").document(uid)
            .update("notification_schedule", schedule).await()
    }

    suspend fun saveBookmark(policyId: String) {
        db.collection("users").document(uid)
            .collection("bookmarks").document(policyId)
            .set(mapOf("bookmarked_at" to System.currentTimeMillis())).await()
    }

    suspend fun removeBookmark(policyId: String) {
        db.collection("users").document(uid)
            .collection("bookmarks").document(policyId)
            .delete().await()
    }

    suspend fun isBookmarked(policyId: String): Boolean =
        db.collection("users").document(uid)
            .collection("bookmarks").document(policyId)
            .get().await().exists()

    suspend fun getBookmarkIds(): List<String> =
        db.collection("users").document(uid)
            .collection("bookmarks").get().await()
            .documents.map { it.id }

    fun isLoggedIn(): Boolean = auth.currentUser != null

    fun displayName(): String? = auth.currentUser?.displayName

    fun email(): String? = auth.currentUser?.email

    fun logout() = auth.signOut()
}
