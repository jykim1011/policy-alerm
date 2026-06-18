package com.policyalarm.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.policyalarm.data.repository.NotificationItem
import com.policyalarm.ui.components.Emoji
import com.policyalarm.ui.components.catEmoji
import com.policyalarm.ui.theme.LocalAppColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    onPolicyClick: (String) -> Unit,
    vm: HistoryViewModel = viewModel(),
) {
    val c = LocalAppColors.current
    val items by vm.items.collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bgApp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.bgSurface)
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("알림", color = c.fgStrong, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (items.isNotEmpty()) {
                Text(
                    "모두 읽음",
                    color = c.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { vm.clearAll() },
                )
            }
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("받은 알림이 없습니다", color = c.fgMuted)
            }
        } else {
            val groups = remember(items) { groupByDay(items) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            ) {
                groups.forEach { (label, groupItems) ->
                    item(key = "h_$label") {
                        Text(
                            label,
                            color = c.fgSubtle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 8.dp),
                        )
                    }
                    items(groupItems.size) { idx ->
                        val it = groupItems[idx]
                        HistoryRow(
                            item = it,
                            onClick = {
                                vm.markRead(it.policyId)
                                onPolicyClick(it.policyId)
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                item {
                    Text(
                        "받은 알림은 30일간 보관됩니다",
                        color = c.fgFaint,
                        fontSize = 11.5.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(item: NotificationItem, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.bgSurface)
            .border(1.dp, c.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(c.govTint),
            contentAlignment = Alignment.Center,
        ) { Emoji(catEmoji(item.category), 21) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("새 ${item.category} 정책", color = c.accent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(timeLabel(item.receivedAt), color = c.fgSubtle, fontSize = 11.5.sp)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                item.title,
                color = c.fgStrong,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!item.isRead) {
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(c.accent),
            )
        }
    }
}

private fun timeLabel(ts: Long): String =
    SimpleDateFormat("a h:mm", Locale.KOREA).format(Date(ts))

private fun dayLabel(ts: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    val yest = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        sameDay(now, then) -> "오늘"
        sameDay(yest, then) -> "어제"
        else -> SimpleDateFormat("M월 d일", Locale.KOREA).format(Date(ts))
    }
}

private fun groupByDay(
    items: List<NotificationItem>,
): List<Pair<String, List<NotificationItem>>> {
    val map = LinkedHashMap<String, MutableList<NotificationItem>>()
    items.forEach { map.getOrPut(dayLabel(it.receivedAt)) { mutableListOf() }.add(it) }
    return map.map { it.key to it.value }
}
