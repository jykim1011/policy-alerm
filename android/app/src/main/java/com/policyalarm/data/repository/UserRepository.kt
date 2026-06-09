package com.policyalarm.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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

    suspend fun updateFcmToken(token: String) {
        db.collection("users").document(uid)
            .update("fcm_token", token).await()
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

    suspend fun countBookmarks(): Int =
        db.collection("users").document(uid)
            .collection("bookmarks").get().await().size()

    suspend fun getBookmarkIds(): List<String> =
        db.collection("users").document(uid)
            .collection("bookmarks").get().await()
            .documents.map { it.id }

    fun isLoggedIn(): Boolean = auth.currentUser != null

    fun displayName(): String? = auth.currentUser?.displayName

    fun email(): String? = auth.currentUser?.email

    fun logout() = auth.signOut()
}
