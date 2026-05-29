package com.policyalarm.ui.screens.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    vm: OnboardingViewModel = viewModel(),
) {
    val selected by vm.selected.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()

    LaunchedEffect(done) { if (done) onComplete() }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Spacer(Modifier.height(48.dp))
        Text("관심 카테고리 선택", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("선택한 카테고리의 정책만 알림으로 받습니다", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))

        ALL_CATEGORIES.forEach { category ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = category in selected,
                    onCheckedChange = { vm.toggle(category) },
                )
                Spacer(Modifier.width(8.dp))
                Text(category, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = vm::confirm,
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Text("시작하기")
        }
    }
}
