package com.policyalarm.ui.screens.splash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.policyalarm.data.repository.UserRepository

@Composable
fun SplashScreen(onLoggedIn: () -> Unit, onNotLoggedIn: () -> Unit) {
    val repo = remember { UserRepository() }
    LaunchedEffect(Unit) {
        if (repo.isLoggedIn()) onLoggedIn() else onNotLoggedIn()
    }
}
