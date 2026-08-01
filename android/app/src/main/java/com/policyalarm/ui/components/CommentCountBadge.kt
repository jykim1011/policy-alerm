package com.policyalarm.ui.components

// 목록 카드의 댓글 수 배지 — 카드가 컴포즈될 때(=화면 근처에 올 때) 1회만
// Firestore 집계를 조회하고 세션 캐시로 재조회를 막는다. 웹 CommentCount 와 동일한 정책.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.policyalarm.data.repository.CommentRepository
import com.policyalarm.ui.theme.LocalAppColors
import java.util.concurrent.ConcurrentHashMap

private object CommentCountCache {
    private val cache = ConcurrentHashMap<String, Int>()
    private val repo by lazy { CommentRepository() }

    fun peek(policyId: String): Int? = cache[policyId]

    suspend fun fetch(policyId: String): Int? =
        cache[policyId]
            ?: runCatching { repo.count(policyId) }.getOrNull()?.also { cache[policyId] = it }
}

@Composable
fun CommentCountBadge(policyId: String, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    val count by produceState(initialValue = CommentCountCache.peek(policyId), policyId) {
        value = CommentCountCache.fetch(policyId)
    }
    val n = count ?: return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = ChatIconVector,
            contentDescription = "댓글 ${n}개",
            tint = c.fgSubtle,
            modifier = Modifier.size(13.dp),
        )
        Text("$n", color = c.fgSubtle, fontSize = 12.sp)
    }
}
