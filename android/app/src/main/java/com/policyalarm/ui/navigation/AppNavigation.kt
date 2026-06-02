package com.policyalarm.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.policyalarm.ui.screens.detail.DetailScreen
import com.policyalarm.ui.screens.detail.DetailViewModelFactory
import com.policyalarm.ui.screens.login.LoginScreen
import com.policyalarm.ui.screens.main.MainScaffold
import com.policyalarm.ui.screens.onboarding.OnboardingScreen
import com.policyalarm.ui.screens.splash.SplashScreen

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val DETAIL = "detail/{policyId}"

    fun detail(policyId: String) = "detail/$policyId"
}

@Composable
fun AppNavigation(startPolicyId: String? = null) {
    val navController = rememberNavController()
    val context = LocalContext.current

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onLoggedIn = {
                    navController.navigate(Routes.MAIN) {
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
                    val dest = if (isNewUser) Routes.ONBOARDING else Routes.MAIN
                    navController.navigate(dest) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.MAIN) {
            MainScaffold(
                onPolicyClick = { policyId -> navController.navigate(Routes.detail(policyId)) },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("policyId") { type = NavType.StringType })
        ) { backStack ->
            val policyId = backStack.arguments?.getString("policyId") ?: return@composable
            val vm = viewModel<com.policyalarm.ui.screens.detail.DetailViewModel>(
                factory = DetailViewModelFactory(context)
            )
            DetailScreen(
                policyId = policyId,
                onBack = { navController.popBackStack() },
                vm = vm,
            )
        }
    }
}
