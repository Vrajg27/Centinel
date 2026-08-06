package com.centinel.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.ui.analytics.AnalyticsScreen
import com.centinel.app.ui.auth.AuthViewModel
import com.centinel.app.ui.auth.LoginScreen
import com.centinel.app.ui.auth.RegisterScreen
import com.centinel.app.ui.dashboard.DashboardScreen
import com.centinel.app.ui.history.HistoryScreen
import com.centinel.app.ui.notifications.NotificationsScreen
import com.centinel.app.ui.scanner.*
import kotlinx.coroutines.launch

@Composable
fun CentinelNavGraph() {
    val context = LocalContext.current
    val repo = remember { CentinelRepository(context) }
    val navController = rememberNavController()
    val authViewModel = remember { AuthViewModel(repo) }

    var startDestination by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        startDestination = if (repo.tokenStore.isLoggedIn()) Screen.Dashboard.route else Screen.Login.route
    }

    if (startDestination == null) return // brief splash while we check stored session

    NavHost(navController = navController, startDestination = startDestination!!) {
        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = authViewModel,
                onLoggedIn = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
            )
        }
        composable(Screen.Register.route) {
            RegisterScreen(
                viewModel = authViewModel,
                onRegistered = { navController.navigate(Screen.Login.route) },
                onNavigateToLogin = { navController.popBackStack() },
            )
        }
        composable(Screen.Dashboard.route) {
            val scope = rememberCoroutineScopeCompat()
            DashboardScreen(
                repo = repo,
                onNavigate = { route -> navController.navigate(route) },
                onLogout = {
                    scope.launch {
                        repo.logout()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                },
            )
        }
        composable(Screen.UrlScanner.route) { UrlScannerScreen(repo) { navController.popBackStack() } }
        composable(Screen.WebsiteScanner.route) { WebsiteScannerScreen(repo) { navController.popBackStack() } }
        composable(Screen.EmailScanner.route) { EmailScannerScreen(repo) { navController.popBackStack() } }
        composable(Screen.HeaderAnalyzer.route) { HeaderAnalyzerScreen(repo) { navController.popBackStack() } }
        composable(Screen.SmsScanner.route) { SmsScannerScreen(repo) { navController.popBackStack() } }
        composable(Screen.QrScanner.route) { QrScannerScreen(repo) { navController.popBackStack() } }
        composable(Screen.FileScanner.route) { FileScannerScreen(repo) { navController.popBackStack() } }
        composable(Screen.PasswordAnalyzer.route) { PasswordAnalyzerScreen(repo) { navController.popBackStack() } }
        composable(Screen.SslChecker.route) { SslCheckerScreen(repo) { navController.popBackStack() } }
        composable(Screen.BreachChecker.route) { BreachCheckerScreen(repo) { navController.popBackStack() } }
        composable(Screen.History.route) { HistoryScreen(repo) { navController.popBackStack() } }
        composable(Screen.Analytics.route) { AnalyticsScreen(repo) { navController.popBackStack() } }
        composable(Screen.Notifications.route) { NotificationsScreen(repo) { navController.popBackStack() } }
    }
}

@Composable
private fun rememberCoroutineScopeCompat() = androidx.compose.runtime.rememberCoroutineScope()
