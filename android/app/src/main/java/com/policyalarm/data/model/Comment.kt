package com.policyalarm.data.model

/** 정책 댓글 한 건. parentId == null 이면 최상위 댓글, 값이 있으면 그 최상위 댓글의 대댓글. */
data class Comment(
    val id: String,
    val authorUid: String,
    val authorNickname: String,
    val text: String,
    val parentId: String?,
    val mentionNickname: String?,
    val createdAtMillis: Long,
    val deleted: Boolean,
)

/** 최상위 댓글과 그 대댓글 목록(깊이 1). */
data class CommentThread(
    val parent: Comment,
    val replies: List<Comment>,
)

/**
 * 평탄한 댓글 리스트를 2단계(댓글→대댓글)로 묶는다. 부모는 작성순(오름차순),
 * 각 부모의 대댓글도 작성순. 부모가 목록에 없는 고아 대댓글은 버린다.
 */
fun groupComments(flat: List<Comment>): List<CommentThread> {
    val parents = flat.filter { it.parentId == null }.sortedBy { it.createdAtMillis }
    val repliesByParent = flat.filter { it.parentId != null }
        .groupBy { it.parentId }
    return parents.map { p ->
        CommentThread(
            parent = p,
            replies = (repliesByParent[p.id] ?: emptyList()).sortedBy { it.createdAtMillis },
        )
    }
}
