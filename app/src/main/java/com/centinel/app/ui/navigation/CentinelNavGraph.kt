package com.centinel.app.ui.navigation

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.centinel.app.CentinelConfig
import com.centinel.app.data.local.SettingsStore
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.ui.analytics.AnalyticsScreen
import com.centinel.app.ui.auth.AuthViewModel
import com.centinel.app.ui.auth.LoginScreen
import com.centinel.app.ui.auth.RegisterScreen
import com.centinel.app.ui.components.CentinelButton
import com.centinel.app.ui.components.CentinelFloatingNavBar
import com.centinel.app.ui.dashboard.DashboardScreen
import com.centinel.app.ui.history.HistoryScreen
import com.centinel.app.ui.notifications.NotificationsScreen
import com.centinel.app.ui.scanner.*
import com.centinel.app.ui.settings.AboutCentinelScreen
import com.centinel.app.ui.settings.BiometricClearanceScreen
import com.centinel.app.ui.settings.ChangePasswordScreen
import com.centinel.app.ui.settings.CommNotificationsScreen
import com.centinel.app.ui.settings.KernelVersionScreen
import com.centinel.app.ui.settings.LanguageDecryptorScreen
import com.centinel.app.ui.settings.NeuralFirewallScreen
import com.centinel.app.ui.settings.PrivacyManifestoScreen
import com.centinel.app.ui.settings.PrivacyProtocolScreen
import com.centinel.app.ui.settings.ProfileDetailsScreen
import com.centinel.app.ui.settings.SettingsScreen
import com.centinel.app.ui.settings.StandardOperationsScreen
import com.centinel.app.ui.settings.TerminateAccountScreen
import com.centinel.app.ui.settings.VisualInterfaceScreen
import com.centinel.app.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun CentinelNavGraph(
    settingsStore: SettingsStore? = null
) {
    val context = LocalContext.current
    val repo = remember { CentinelRepository(context) }
    val navController = rememberNavController()
    val authViewModel = remember { AuthViewModel(repo) }

    var startDestination by remember { mutableStateOf<String?>(null) }
    var isBiometricUnlocked by remember { mutableStateOf(false) }

    val activeSettings = settingsStore ?: repo.settingsStore

    LaunchedEffect(Unit) {
        try {
            delay(500)
            val isLoggedIn = repo.tokenStore.isLoggedIn()
            startDestination = if (isLoggedIn) Screen.Dashboard.route else Screen.Login.route
            if (!activeSettings.biometricEnabled || !isLoggedIn) {
                isBiometricUnlocked = true
            }
        } catch (e: Exception) {
            startDestination = Screen.Login.route
            isBiometricUnlocked = true
        }
    }

    LaunchedEffect(Unit) {
        repo.unauthorizedEvents.collect {
            if (!CentinelConfig.isAdminTestMode) {
                repo.logout()
                navController.navigate(Screen.Login.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    if (startDestination == null) {
        CentinelLoadingScreen()
        return
    }

    if (activeSettings.biometricEnabled && !isBiometricUnlocked && startDestination != Screen.Login.route) {
        BiometricLockOverlay(
            onUnlocked = { isBiometricUnlocked = true }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        NavHost(
            navController = navController,
            startDestination = startDestination!!,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(500)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(500)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(500)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(500)) }
        ) {
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
                DashboardScreen(
                    repo = repo,
                    onNavigate = { route -> navController.navigate(route) }
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
            composable(Screen.Settings.route) {
                SettingsScreen(
                    repo = repo,
                    onBack = { navController.popBackStack() },
                    onLogout = {
                        CentinelConfig.isAdminTestMode = false
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onNavigateToProfileDetails = { navController.navigate(Screen.ProfileDetails.route) },
                    onNavigateToChangePassword = { navController.navigate(Screen.ChangePassword.route) },
                    onNavigateToTerminateAccount = { navController.navigate(Screen.TerminateAccount.route) },
                    onNavigateToNeuralFirewall = { navController.navigate(Screen.NeuralFirewall.route) },
                    onNavigateToPrivacyProtocol = { navController.navigate(Screen.PrivacyProtocol.route) },
                    onNavigateToBiometricClearance = { navController.navigate(Screen.BiometricClearance.route) },
                    onNavigateToCommNotifications = { navController.navigate(Screen.CommNotifications.route) },
                    onNavigateToVisualInterface = { navController.navigate(Screen.VisualInterface.route) },
                    onNavigateToLanguageDecryptor = { navController.navigate(Screen.LanguageDecryptor.route) },
                    onNavigateToAboutCentinel = { navController.navigate(Screen.AboutCentinel.route) },
                    onNavigateToPrivacyManifesto = { navController.navigate(Screen.PrivacyManifesto.route) },
                    onNavigateToStandardOperations = { navController.navigate(Screen.StandardOperations.route) },
                    onNavigateToKernelVersion = { navController.navigate(Screen.KernelVersion.route) }
                )
            }
            composable(Screen.ProfileDetails.route) {
                ProfileDetailsScreen(
                    repo = repo,
                    onBack = { navController.popBackStack() },
                    onLogout = {
                        CentinelConfig.isAdminTestMode = false
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.ChangePassword.route) {
                ChangePasswordScreen(
                    repo = repo,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.TerminateAccount.route) {
                TerminateAccountScreen(
                    repo = repo,
                    onBack = { navController.popBackStack() },
                    onLogout = {
                        CentinelConfig.isAdminTestMode = false
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.NeuralFirewall.route) {
                NeuralFirewallScreen(
                    repo = repo,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.PrivacyProtocol.route) {
                PrivacyProtocolScreen(
                    repo = repo,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.BiometricClearance.route) {
                BiometricClearanceScreen(
                    repo = repo,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.CommNotifications.route) {
                CommNotificationsScreen(
                    repo = repo,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.VisualInterface.route) {
                VisualInterfaceScreen(
                    repo = repo,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.LanguageDecryptor.route) {
                LanguageDecryptorScreen(
                    repo = repo,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.AboutCentinel.route) {
                AboutCentinelScreen(
                    repo = repo,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.PrivacyManifesto.route) {
                PrivacyManifestoScreen(
                    repo = repo,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.StandardOperations.route) {
                StandardOperationsScreen(
                    repo = repo,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.KernelVersion.route) {
                KernelVersionScreen(
                    repo = repo,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            CentinelBottomBar(navController)
        }

        if (CentinelConfig.isAdminTestMode) {
            AdminModeIndicator()
        }
    }
}

@Composable
fun BiometricLockOverlay(
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    var authError by remember { mutableStateOf<String?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }

    fun triggerBiometricPrompt() {
        isAuthenticating = true
        authError = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && context is FragmentActivity) {
            try {
                val executor = ContextCompat.getMainExecutor(context)
                val biometricPrompt = androidx.biometric.BiometricPrompt(
                    context,
                    executor,
                    object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                            isAuthenticating = false
                            onUnlocked()
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            isAuthenticating = false
                            if (errorCode == androidx.biometric.BiometricPrompt.ERROR_NO_BIOMETRICS ||
                                errorCode == androidx.biometric.BiometricPrompt.ERROR_HW_NOT_PRESENT ||
                                errorCode == androidx.biometric.BiometricPrompt.ERROR_HW_UNAVAILABLE
                            ) {
                                onUnlocked()
                            } else {
                                authError = errString.toString()
                            }
                        }
                        override fun onAuthenticationFailed() {
                            isAuthenticating = false
                            authError = "Biometric clearance rejected. Try again."
                        }
                    }
                )

                val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Centinel Terminal Clearance")
                    .setSubtitle("Biometric authentication required")
                    .setNegativeButtonText("Cancel")
                    .build()

                biometricPrompt.authenticate(promptInfo)
            } catch (e: Exception) {
                isAuthenticating = false
                onUnlocked()
            }
        } else {
            isAuthenticating = false
            onUnlocked()
        }
    }

    LaunchedEffect(Unit) {
        triggerBiometricPrompt()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue.copy(alpha = 0.15f))
                    .border(2.dp, AccentCyan, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "BIOMETRIC CLEARANCE REQUIRED",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Centinel terminal is locked. Authenticate to proceed.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            if (authError != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    authError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = DangerRed,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(32.dp))

            CentinelButton(
                text = "Scan Biometrics",
                onClick = { triggerBiometricPrompt() },
                loading = isAuthenticating,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Lock
            )
        }
    }
}

@Composable
fun CentinelLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .background(Brush.radialGradient(listOf(GlowBlue, Color.Transparent)), CircleShape)
        )
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = AccentCyan, 
                strokeWidth = 4.dp,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "CENTINEL PROTOCOL INITIALIZING",
                color = TextPrimary,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                "Establishing secure link...",
                color = AccentCyan.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun AdminModeIndicator() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 10.dp, end = 120.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        Surface(
            color = AccentCyan.copy(alpha = 0.15f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "ADMIN TEST MODE",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = AccentCyan,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun CentinelBottomBar(navController: NavController) {
    val items = remember {
        listOf(
            Screen.Dashboard,
            Screen.History,
            Screen.Analytics,
            Screen.Notifications
        )
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in items.map { it.route }

    if (showBottomBar) {
        CentinelFloatingNavBar(
            items = items,
            currentRoute = currentRoute,
            onNavigate = { route ->
                if (currentRoute != route) {
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        )
    }
}
