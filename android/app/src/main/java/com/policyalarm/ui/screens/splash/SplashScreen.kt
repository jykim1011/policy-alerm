package com.policyalarm.ui.screens.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.policyalarm.data.repository.UserRepository
import com.policyalarm.ui.components.PolicyAppIcon
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onLoggedIn: () -> Unit, onNotLoggedIn: () -> Unit) {
    val repo = remember { UserRepository() }
    LaunchedEffect(Unit) {
        val loggedIn = repo.isLoggedIn()
        delay(1300)
        if (loggedIn) onLoggedIn() else onNotLoggedIn()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF2F63F0), Color(0xFF1D4ED8), Color(0xFF16245E))
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            PolicyAppIcon(size = 120, corner = 26)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "정책 알리미",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "새 정책, 발표 즉시 알려드려요",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
        CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 3.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
                .size(26.dp),
        )
    }
}
