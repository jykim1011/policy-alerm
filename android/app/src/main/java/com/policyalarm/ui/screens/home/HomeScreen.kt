package com.policyalarm.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.policyalarm.data.model.PolicyItem

val CATEGORIES = listOf("전체", "청약", "대출", "세금", "재개발", "전월세")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPolicyClick: (String) -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    vm: HomeViewModel,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("정책 알림") },
                actions = {
                    IconButton(onClick = onHistoryClick) {
                        Icon(Icons.Default.History, "알림 기록")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, "설정")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                items(CATEGORIES) { category ->
                    FilterChip(
                        selected = state.selectedCategory == category,
                        onClick = { vm.selectCategory(category) },
                        label = { Text(category) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            when {
                state.isLoading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                state.error != null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error!!)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = vm::loadPolicies) { Text("다시 시도") }
                    }
                }

                else -> LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(state.policies, key = { it.id }) { policy ->
                        PolicyCard(
                            policy = policy,
                            isRead = policy.id in state.readIds,
                            onClick = { onPolicyClick(policy.id) },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun PolicyCard(policy: PolicyItem, isRead: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isRead) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row {
                AssistChip(
                    onClick = {},
                    label = { Text(policy.subcategory, style = MaterialTheme.typography.labelSmall) },
                )
                Spacer(Modifier.weight(1f))
                Text(policy.publishedAt.take(10), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(4.dp))
            Text(policy.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(
                policy.summaryPreview,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                policy.source,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
