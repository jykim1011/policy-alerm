package com.policyalarm.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.policyalarm.ui.screens.detail.DetailScreen
import com.policyalarm.ui.screens.history.HistoryScreen
import com.policyalarm.ui.screens.home.HomeScreen
import com.policyalarm.ui.screens.login.LoginScreen
import com.policyalarm.ui.screens.onboarding.OnboardingScreen
import com.policyalarm.ui.screens.settings.SettingsScreen
import com.policyalarm.ui.screens.splash.SplashScreen

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val DETAIL = "detail/{policyId}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    fun detail(policyId: String) = "detail/$policyId"
}

@Composable
fun AppNavigation(startPolicyId: String? = null) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onLoggedIn = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNotLoggedIn = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = { isNewUser ->
                    if (isNewUser) {
                        navController.navigate(Routes.ONBOARDING) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                }
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onPolicyClick = { policyId -> navController.navigate(Routes.detail(policyId)) },
                onHistoryClick = { navController.navigate(Routes.HISTORY) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("policyId") { type = NavType.StringType })
        ) { backStack ->
            val policyId = backStack.arguments?.getString("policyId") ?: return@composable
            DetailScreen(policyId = policyId, onBack = { navController.popBackStack() })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onPolicyClick = { policyId -> navController.navigate(Routes.detail(policyId)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
