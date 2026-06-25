package com.policyalarm.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
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
        Spacer(Modifier.height(14.dp))

        // 답글 대상 안내 — 둥근 pill 배지
        replyTarget?.let { (_, nick) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.govTint)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "@$nick 님에게 답글",
                    color = c.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "취소",
                    color = c.fgSubtle,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { replyTarget = null },
                )
            }
        }

        // 입력창 — 외곽선 없는 둥근 필드 + 내부 원형 전송 버튼
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(c.bgSurface2)
                .padding(start = 16.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 9.dp)) {
                if (input.isEmpty()) {
                    Text("댓글을 입력하세요", color = c.fgFaint, fontSize = 14.sp)
                }
                BasicTextField(
                    value = input,
                    onValueChange = { if (it.length <= 1000) input = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = c.fgDefault, fontSize = 14.sp),
                    cursorBrush = SolidColor(c.accent),
                    maxLines = 4,
                )
            }
            val canSend = input.isNotBlank()
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (canSend) c.accent else Color.Transparent)
                    .clickable(enabled = canSend) {
                        onPost(input, replyTarget?.first, replyTarget?.second)
                        input = ""
                        replyTarget = null
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    "등록",
                    tint = if (canSend) Color.White else c.fgFaint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        threads.forEach { thread ->
            CommentRow(thread.parent, myUid, isReply = false,
                onReply = { replyTarget = thread.parent.id to thread.parent.authorNickname },
                onDelete = { onDelete(thread.parent.id) })
            thread.replies.forEach { reply ->
                CommentRow(reply, myUid, isReply = true,
                    onReply = { replyTarget = thread.parent.id to reply.authorNickname },
                    onDelete = { onDelete(reply.id) })
            }
            Spacer(Modifier.height(16.dp))
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isReply) 16.dp else 0.dp, bottom = 8.dp)
            .height(IntrinsicSize.Min),
    ) {
        // 대댓글: 왼쪽 얇은 accent 레일로 스레드 소속을 표시
        if (isReply) {
            Box(
                Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(1.dp))
                    .background(c.accent.copy(alpha = 0.35f)),
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isReply) c.bgSurface2 else c.bgSurface)
                .padding(14.dp),
        ) {
            Text(comment.authorNickname, color = c.fgStrong, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            if (comment.deleted) {
                Text("삭제된 댓글입니다", color = c.fgFaint, fontSize = 13.sp)
            } else {
                val body = comment.mentionNickname?.let { "@$it ${comment.text}" } ?: comment.text
                Text(body, color = c.fgDefault, fontSize = 14.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(9.dp))
                Row {
                    Text("답글", color = c.fgSubtle, fontSize = 11.5.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { onReply() })
                    if (comment.authorUid == myUid) {
                        Text("삭제", color = c.danger, fontSize = 11.5.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 16.dp).clickable { onDelete() })
                    }
                }
            }
        }
    }
}
