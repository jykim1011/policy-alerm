package com.policyalarm.ui.screens.archive

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.policyalarm.ui.screens.home.PolicyCard
import com.policyalarm.ui.theme.LocalAppColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArchiveScreen(
    onBack: () -> Unit,
    onPolicyClick: (String) -> Unit,
    vm: ArchiveViewModel,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val c = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bgApp),
    ) {
        // top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.bgSurface)
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.ArrowBack, "뒤로", tint = c.fgMuted, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(4.dp))
            Text(
                "정책 아카이브",
                color = c.fgStrong,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }

        // year selector (only shown when >1 year)
        if (state.availableYears.size > 1) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.bgSurface)
                    .border(1.dp, c.border),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.availableYears) { year ->
                    YearChip(
                        label = "${year}년",
                        selected = state.selectedYear == year,
                        onClick = { vm.selectYear(year) },
                    )
                }
            }
        }

        when {
            state.isLoading && state.sections.isEmpty() -> Box(
                Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = c.accent) }

            state.error != null -> Box(
                Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) { Text(state.error!!, color = c.fgMuted) }

            state.sections.isEmpty() -> Box(
                Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) { Text("정책이 없어요", color = c.fgMuted) }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                state.sections.forEach { section ->
                    stickyHeader(key = section.yearMonth) {
                        SectionHeader(section.label)
                    }
                    items(section.items, key = { it.id }) { policy ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                            PolicyCard(
                                policy = policy,
                                isRead = policy.id in state.readIds,
                                onClick = { onPolicyClick(policy.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    val c = LocalAppColors.current
    Text(
        label,
        color = c.fgSubtle,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .background(c.bgApp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun YearChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val bg = if (selected) c.accent else c.bgSurface
    val fg = if (selected) Color.White else c.fgDefault
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, if (selected) c.accent else c.border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
