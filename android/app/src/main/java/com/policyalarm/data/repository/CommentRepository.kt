package com.policyalarm.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.policyalarm.data.model.Comment
import kotlinx.coroutines.tasks.await

class CommentRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private val uid get() = auth.currentUser?.uid ?: error("로그인 필요")

    private fun items(policyId: String) =
        db.collection("comments").document(policyId).collection("items")

    /** 댓글/대댓글 작성. parentId=null이면 최상위 댓글. 생성된 문서 id 반환. */
    suspend fun addComment(
        policyId: String,
        text: String,
        authorNickname: String,
        parentId: String? = null,
        mentionNickname: String? = null,
    ): String {
        val ref = items(policyId).document()
        ref.set(
            mapOf(
                "authorUid" to uid,
                "authorNickname" to authorNickname,
                "text" to text,
                "parentId" to parentId,
                "mentionNickname" to mentionNickname,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "deleted" to false,
            )
        ).await()
        return ref.id
    }

    /**
     * 최상위 댓글을 최신순 limit개 가져오고, 그 부모들의 대댓글을 함께 가져와 평탄 리스트로 반환한다.
     * 호출부(ViewModel)는 groupComments()로 2단계 구조로 묶는다.
     * startAfterMillis: 더보기 페이지네이션 커서(이전 페이지 마지막 부모의 createdAt millis).
     */
    suspend fun getComments(policyId: String, limit: Long = 20, startAfterMillis: Long? = null): List<Comment> {
        var q: Query = items(policyId)
            .whereEqualTo("parentId", null)
            .orderBy("createdAt", Query.Direction.DESCENDING)
        if (startAfterMillis != null) q = q.startAfter(com.google.firebase.Timestamp(startAfterMillis / 1000, 0))
        val parentDocs = q.limit(limit).get().await().documents
        val parents = parentDocs.map { it.toComment() }
        if (parents.isEmpty()) return emptyList()

        // 부모 id들의 대댓글 (Firestore whereIn 최대 30개 → 청크)
        val replies = mutableListOf<Comment>()
        val parentIds = parents.map { it.id }
        for (i in parentIds.indices step 30) {
            val chunk = parentIds.subList(i, minOf(i + 30, parentIds.size))
            val snap = items(policyId).whereIn("parentId", chunk).get().await()
            snap.documents.forEach { replies.add(it.toComment()) }
        }
        return parents + replies
    }

    suspend fun softDelete(policyId: String, commentId: String) {
        items(policyId).document(commentId).update("deleted", true).await()
    }

    suspend fun count(policyId: String): Int =
        items(policyId).count().get(AggregateSource.SERVER).await().count.toInt()

    private fun com.google.firebase.firestore.DocumentSnapshot.toComment(): Comment {
        val ts = getTimestamp("createdAt")
        return Comment(
            id = id,
            authorUid = getString("authorUid") ?: "",
            authorNickname = getString("authorNickname") ?: "익명",
            text = getString("text") ?: "",
            parentId = getString("parentId"),
            mentionNickname = getString("mentionNickname"),
            createdAtMillis = ts?.toDate()?.time ?: 0L,
            deleted = getBoolean("deleted") ?: false,
        )
    }
}
