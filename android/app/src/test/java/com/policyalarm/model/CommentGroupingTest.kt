package com.policyalarm.model

import com.policyalarm.data.model.Comment
import com.policyalarm.data.model.groupComments
import org.junit.Assert.assertEquals
import org.junit.Test

class CommentGroupingTest {

    private fun c(id: String, parent: String?, t: Long) =
        Comment(id = id, authorUid = "u", authorNickname = "닉", text = "x",
            parentId = parent, mentionNickname = null, createdAtMillis = t, deleted = false)

    @Test
    fun `최상위 댓글과 대댓글을 2단계로 묶는다`() {
        val flat = listOf(
            c("a", null, 100),
            c("b", null, 200),
            c("a1", "a", 150),
            c("a2", "a", 120),
        )
        val threads = groupComments(flat)
        assertEquals(listOf("a", "b"), threads.map { it.parent.id })   // 부모 오름차순
        assertEquals(listOf("a2", "a1"), threads[0].replies.map { it.id }) // reply 오름차순
        assertEquals(emptyList<String>(), threads[1].replies.map { it.id })
    }

    @Test
    fun `부모 없는 고아 대댓글은 제외한다`() {
        val flat = listOf(c("a", null, 100), c("x1", "ghost", 150))
        val threads = groupComments(flat)
        assertEquals(1, threads.size)
        assertEquals("a", threads[0].parent.id)
    }
}
