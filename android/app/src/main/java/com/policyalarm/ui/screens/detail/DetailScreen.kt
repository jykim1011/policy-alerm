package com.policyalarm.ui.screens.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    policyId: String,
    onBack: () -> Unit,
    vm: DetailViewModel,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(policyId) { vm.load(policyId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("정책 상세") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.toggleBookmark(policyId) }) {
                        Icon(
                            if (state.isBookmarked) Icons.Default.Bookmark
                            else Icons.Default.BookmarkBorder,
                            "북마크",
                        )
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.error != null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { Text(state.error!!) }

            state.detail != null -> {
                val detail = state.detail!!
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    AssistChip(onClick = {}, label = { Text(detail.subcategory) })
                    Spacer(Modifier.height(8.dp))
                    Text(detail.title, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${detail.source} · ${detail.publishedAt.take(10)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (detail.summary != null) {
                        Spacer(Modifier.height(24.dp))
                        SummaryCard("무엇이 바뀌었나", detail.summary.whatChanged)
                        Spacer(Modifier.height(8.dp))
                        SummaryCard("누가 대상인가", detail.summary.whoIsAffected)
                        Spacer(Modifier.height(8.dp))
                        SummaryCard("언제부터 적용되나", detail.summary.whenEffective)

                        if (detail.summary.keyPoints.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Text("핵심 포인트", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            detail.summary.keyPoints.forEach { point ->
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text("• ", style = MaterialTheme.typography.bodyMedium)
                                    Text(point, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    OutlinedButton(
                        onClick = {
                            val url = detail.fileUrl ?: detail.sourceUrl
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("원문 보기")
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCard(label: String, content: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
