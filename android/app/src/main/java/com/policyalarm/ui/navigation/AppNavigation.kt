package com.policyalarm.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
fun AppNavigation(
    deepLinkPolicyId: String? = null,
    onDeepLinkHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    // 로그인해서 MAIN에 도달하면 true. 이후 들어오는 알림 딥링크는 바로 상세로 이동한다.
    var readyForDeepLink by rememberSaveable { mutableStateOf(false) }

    // 콜드 스타트(onCreate)·앱 실행 중(onNewIntent) 모두 deepLinkPolicyId가 갱신되며,
    // 준비되면(로그인 완료) 상세로 이동하고 소비한다.
    LaunchedEffect(deepLinkPolicyId, readyForDeepLink) {
        val id = deepLinkPolicyId
        if (readyForDeepLink && id != null) {
            navController.navigate(Routes.detail(id))
            onDeepLinkHandled()
        }
    }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onLoggedIn = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                    readyForDeepLink = true
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
                    if (!isNewUser) readyForDeepLink = true
                }
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                    readyForDeepLink = true
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
