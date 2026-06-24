package com.policyalarm.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.policyalarm.data.model.Comment
import com.policyalarm.data.model.CommentThread
import com.policyalarm.ui.theme.LocalAppColors

/**
 * 정책 상세 하단 댓글 영역. 입력창 + 2단계(댓글→대댓글) 목록.
 * reply 대상이 선택되면 그 부모 id/닉네임으로 대댓글을 단다.
 */
@Composable
fun CommentSection(
    threads: List<CommentThread>,
    commentCount: Int,
    myUid: String?,
    onPost: (text: String, parentId: String?, mentionNickname: String?) -> Unit,
    onDelete: (commentId: String) -> Unit,
) {
    val c = LocalAppColors.current
    var input by remember { mutableStateOf("") }
    // (부모 id, 멘션 닉네임) — null이면 최상위 댓글
    var replyTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("댓글 $commentCount", color = c.fgStrong, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        replyTarget?.let { (_, nick) ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Text("@$nick 님에게 답글", color = c.accent, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("취소", color = c.fgSubtle, fontSize = 12.sp, modifier = Modifier.clickable { replyTarget = null })
            }
        }

        Row(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = input,
                onValueChange = { if (it.length <= 1000) input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("댓글을 입력하세요") },
                maxLines = 4,
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.Send, "등록",
                tint = if (input.isBlank()) c.fgFaint else c.accent,
                modifier = Modifier
                    .padding(start = 8.dp, top = 12.dp)
                    .clickable(enabled = input.isNotBlank()) {
                        onPost(input, replyTarget?.first, replyTarget?.second)
                        input = ""
                        replyTarget = null
                    },
            )
        }

        Spacer(Modifier.height(16.dp))
        threads.forEach { thread ->
            CommentRow(thread.parent, myUid, isReply = false,
                onReply = { replyTarget = thread.parent.id to thread.parent.authorNickname },
                onDelete = { onDelete(thread.parent.id) })
            thread.replies.forEach { reply ->
                CommentRow(reply, myUid, isReply = true,
                    onReply = { replyTarget = thread.parent.id to reply.authorNickname },
                    onDelete = { onDelete(reply.id) })
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun CommentRow(
    comment: Comment,
    myUid: String?,
    isReply: Boolean,
    onReply: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = if (isReply) 20.dp else 0.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.bgSurface)
            .padding(12.dp),
    ) {
        Text(comment.authorNickname, color = c.fgStrong, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        if (comment.deleted) {
            Text("삭제된 댓글입니다", color = c.fgFaint, fontSize = 13.sp)
        } else {
            val body = comment.mentionNickname?.let { "@$it ${comment.text}" } ?: comment.text
            Text(body, color = c.fgDefault, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Row {
                Text("답글", color = c.fgSubtle, fontSize = 11.5.sp, modifier = Modifier.clickable { onReply() })
                if (comment.authorUid == myUid) {
                    Text("삭제", color = c.danger, fontSize = 11.5.sp,
                        modifier = Modifier.padding(start = 14.dp).clickable { onDelete() })
                }
            }
        }
    }
}
