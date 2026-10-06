package com.centinel.app.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.centinel.app.data.local.SettingsStore
import kotlinx.coroutines.launch
import com.centinel.app.data.model.UserOut
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.di.ViewModelFactory
import com.centinel.app.ui.common.UiState
import com.centinel.app.ui.components.*
import com.centinel.app.ui.theme.*

@Composable
fun SettingsScreen(
    repo: CentinelRepository,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToProfileDetails: () -> Unit = {},
    onNavigateToChangePassword: () -> Unit = {},
    onNavigateToTerminateAccount: () -> Unit = {},
    onNavigateToNeuralFirewall: () -> Unit = {},
    onNavigateToPrivacyProtocol: () -> Unit = {},
    onNavigateToBiometricClearance: () -> Unit = {},
    onNavigateToCommNotifications: () -> Unit = {},
    onNavigateToVisualInterface: () -> Unit = {},
    onNavigateToLanguageDecryptor: () -> Unit = {},
    onNavigateToAboutCentinel: () -> Unit = {},
    onNavigateToPrivacyManifesto: () -> Unit = {},
    onNavigateToStandardOperations: () -> Unit = {},
    onNavigateToKernelVersion: () -> Unit = {}
) {
    val vm: SettingsViewModel = viewModel(factory = ViewModelFactory(repo))
    val settingsStore = vm.settingsStore
    val userState by vm.userState.collectAsState()

    var showLogoutConfirm by remember { mutableStateOf(false) }

    // Dialog state controllers
    var showProfileDetailsDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var showFirewallDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showBiometricDialog by remember { mutableStateOf(false) }

    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showVisualDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    var showAboutDialog by remember { mutableStateOf(false) }
    var showPrivacyManifestoDialog by remember { mutableStateOf(false) }
    var showStandardOperationsDialog by remember { mutableStateOf(false) }
    var showKernelVersionDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        vm.loadUser()
    }

    val currentUser = (userState as? UiState.Success)?.data

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(SurfaceVariant, CircleShape).border(1.dp, GlassBorder, CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    "Command Settings",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 100.dp, y = 100.dp)
                    .background(Brush.radialGradient(listOf(GlowPurple.copy(alpha = 0.1f), Color.Transparent)), CircleShape)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(24.dp)
            ) {
                item {
                    when (val state = userState) {
                        is UiState.Success -> RedesignedUserProfileSection(
                            user = state.data,
                            onEditProfile = { onNavigateToProfileDetails() }
                        )
                        is UiState.Loading -> Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AccentCyan)
                        }
                        else -> {}
                    }
                    Spacer(Modifier.height(32.dp))
                }

                item {
                    SettingsGroupRED(title = "Account Clearance") {
                        SettingsItemRED(
                            Icons.Default.Person,
                            "Profile Details",
                            onClick = { onNavigateToProfileDetails() }
                        )
                        SettingsItemRED(
                            Icons.Default.Password,
                            "Change Password",
                            onClick = { onNavigateToChangePassword() }
                        )
                        SettingsItemRED(
                            Icons.Default.Delete,
                            "Terminate Account",
                            textColor = DangerRed,
                            onClick = { onNavigateToTerminateAccount() }
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }

                item {
                    SettingsGroupRED(title = "Encryption & Security") {
                        SettingsItemRED(Icons.Default.Shield, "Neural Firewall", onClick = { onNavigateToNeuralFirewall() })
                        SettingsItemRED(Icons.Default.Lock, "Privacy Protocol", onClick = { onNavigateToPrivacyProtocol() })
                        SettingsItemRED(Icons.Default.Fingerprint, "Biometric Clearance", onClick = { onNavigateToBiometricClearance() })
                    }
                    Spacer(Modifier.height(24.dp))
                }

                item {
                    SettingsGroupRED(title = "Application") {
                        SettingsItemRED(Icons.Default.Notifications, "Comm Notifications", onClick = { onNavigateToCommNotifications() })
                        SettingsItemRED(Icons.Default.Palette, "Visual Interface", onClick = { onNavigateToVisualInterface() })
                        SettingsItemRED(Icons.Default.Language, "Language Decryptor", onClick = { onNavigateToLanguageDecryptor() })
                    }
                    Spacer(Modifier.height(24.dp))
                }

                item {
                    SettingsGroupRED(title = "Intelligence Info") {
                        SettingsItemRED(Icons.Default.Info, "About Centinel AI", onClick = { onNavigateToAboutCentinel() })
                        SettingsItemRED(Icons.Default.Description, "Privacy Manifesto", onClick = { onNavigateToPrivacyManifesto() })
                        SettingsItemRED(Icons.Default.Gavel, "Standard Operations", onClick = { onNavigateToStandardOperations() })
                        SettingsItemRED(Icons.Default.History, "Kernel Version", trailing = "2026.1.0", onClick = { onNavigateToKernelVersion() })
                    }
                    Spacer(Modifier.height(32.dp))
                }

                item {
                    CentinelButton(
                        text = "Sign Out of Terminal",
                        onClick = { showLogoutConfirm = true },
                        colors = listOf(SurfaceVariant, Surface),
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.AutoMirrored.Filled.Logout
                    )
                    Spacer(Modifier.height(64.dp))
                }
            }
        }

        // Dialogs
        if (showProfileDetailsDialog && currentUser != null) {
            ProfileDetailsDialog(
                user = currentUser,
                onDismiss = { showProfileDetailsDialog = false },
                onSave = { newName ->
                    vm.updateProfile(
                        fullName = newName,
                        onSuccess = {
                            showProfileDetailsDialog = false
                            scope.launch { snackbarHostState.showSnackbar("Profile details updated successfully.") }
                        },
                        onError = { err ->
                            scope.launch { snackbarHostState.showSnackbar("Failed to update profile: $err") }
                        }
                    )
                }
            )
        }

        if (showChangePasswordDialog) {
            ChangePasswordDialog(
                onDismiss = { showChangePasswordDialog = false },
                onSubmit = { oldPass, newPass, onError ->
                    vm.changePassword(
                        oldPass = oldPass,
                        newPass = newPass,
                        onSuccess = {
                            showChangePasswordDialog = false
                            scope.launch { snackbarHostState.showSnackbar("Password updated successfully.") }
                        },
                        onError = onError
                    )
                }
            )
        }

        if (showDeleteConfirm) {
            CentinelDialog(
                onDismiss = { showDeleteConfirm = false },
                title = "Terminate Account?",
                confirmButton = {
                    CentinelButton(
                        "Confirm Termination",
                        onClick = {
                            showDeleteConfirm = false
                            vm.deleteAccount(onLogout)
                        },
                        colors = listOf(DangerRed, Color(0xFF990000))
                    )
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Abort", color = TextSecondary)
                    }
                }
            ) {
                Text(
                    "This will permanently purge your account and all decentralized scan history. This action is irreversible.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (showLogoutConfirm) {
            CentinelDialog(
                onDismiss = { showLogoutConfirm = false },
                title = "Sign Out of Terminal?",
                confirmButton = {
                    CentinelButton(
                        "Confirm Sign Out",
                        onClick = {
                            showLogoutConfirm = false
                            vm.logout(onLogout)
                        },
                        colors = listOf(PrimaryBlue, SecondaryPurple)
                    )
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutConfirm = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            ) {
                Text(
                    "Are you sure you want to sign out? You will be returned to the login clearance screen.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (showFirewallDialog) {
            NeuralFirewallDialog(
                settingsStore = settingsStore,
                onDismiss = { showFirewallDialog = false },
                onUpdated = { scope.launch { snackbarHostState.showSnackbar("Neural Firewall settings updated.") } }
            )
        }

        if (showPrivacyDialog) {
            PrivacyProtocolDialog(
                settingsStore = settingsStore,
                onDismiss = { showPrivacyDialog = false },
                onClearCache = { bytesFreed ->
                    val mb = bytesFreed / (1024 * 1024)
                    scope.launch { snackbarHostState.showSnackbar("Cleared $mb MB of temporary scan artifacts.") }
                }
            )
        }

        if (showBiometricDialog) {
            BiometricClearanceDialog(
                settingsStore = settingsStore,
                onDismiss = { showBiometricDialog = false },
                onTestBiometrics = {
                    scope.launch { snackbarHostState.showSnackbar("Biometric clearance verified. Hardware active.") }
                }
            )
        }

        if (showNotificationsDialog) {
            CommNotificationsDialog(
                settingsStore = settingsStore,
                onDismiss = { showNotificationsDialog = false }
            )
        }

        if (showVisualDialog) {
            VisualInterfaceDialog(
                settingsStore = settingsStore,
                onDismiss = { showVisualDialog = false }
            )
        }

        if (showLanguageDialog) {
            LanguageDecryptorDialog(
                settingsStore = settingsStore,
                onDismiss = { showLanguageDialog = false },
                onLanguageChanged = { lang ->
                    scope.launch { snackbarHostState.showSnackbar("System language updated to $lang.") }
                }
            )
        }

        if (showAboutDialog) {
            AboutCentinelDialog(
                onDismiss = { showAboutDialog = false },
                onRunDiagnostics = {
                    scope.launch { snackbarHostState.showSnackbar("Diagnostics complete: All 10 Neural Nodes online.") }
                }
            )
        }

        if (showPrivacyManifestoDialog) {
            PrivacyManifestoDialog(onDismiss = { showPrivacyManifestoDialog = false })
        }

        if (showStandardOperationsDialog) {
            StandardOperationsDialog(onDismiss = { showStandardOperationsDialog = false })
        }

        if (showKernelVersionDialog) {
            KernelVersionDialog(onDismiss = { showKernelVersionDialog = false })
        }
    }
}

// ==========================================
// User Profile Section
// ==========================================

@Composable
fun RedesignedUserProfileSection(
    user: UserOut,
    onEditProfile: () -> Unit = {}
) {
    CentinelGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditProfile() },
        glowColor = PrimaryGlow.copy(alpha = 0.15f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(PremiumGradient))
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(SurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    val initials = remember(user.full_name) {
                        val parts = (user.full_name ?: "").trim().split(" ").filter { it.isNotBlank() }
                        if (parts.size >= 2) {
                            "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
                        } else if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
                            parts[0].take(2).uppercase()
                        } else {
                            "C"
                        }
                    }
                    Text(
                        initials,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    user.full_name?.ifBlank { null } ?: "Commander Centinel",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = if (user.is_admin) PrimaryBlue.copy(alpha = 0.15f) else AccentCyan.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, if (user.is_admin) PrimaryBlue.copy(alpha = 0.3f) else AccentCyan.copy(alpha = 0.3f))
                ) {
                    Text(
                        if (user.is_admin) "LEVEL 5 ADMIN" else "SECURITY CLEARANCE: COMMANDER",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = if (user.is_admin) PrimaryBlue else AccentCyan,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            IconButton(
                onClick = onEditProfile,
                modifier = Modifier
                    .size(36.dp)
                    .background(SurfaceVariant, CircleShape)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit Profile",
                    tint = PrimaryBlue,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ==========================================
// Dialog Components
// ==========================================

@Composable
fun ProfileDetailsDialog(
    user: UserOut,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var fullName by remember { mutableStateOf(user.full_name ?: "") }
    var isSaving by remember { mutableStateOf(false) }

    CentinelDialog(
        onDismiss = onDismiss,
        title = "Profile Details",
        confirmButton = {
            CentinelButton(
                text = "Save Changes",
                onClick = {
                    isSaving = true
                    onSave(fullName.trim())
                },
                loading = isSaving,
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel", color = TextSecondary)
            }
        }
    ) {
        Column(modifier = Modifier.padding(top = 8.dp)) {
            CentinelTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = "Full Name",
                icon = Icons.Default.Person,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            CentinelTextField(
                value = user.email,
                onValueChange = {},
                label = "Email Address (Read-only)",
                icon = Icons.Default.Email,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Role: ${if (user.is_admin) "Administrator" else "Standard User"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onSubmit: (oldPass: String, newPass: String, onError: (String) -> Unit) -> Unit
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var oldVisible by remember { mutableStateOf(false) }
    var newVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    CentinelDialog(
        onDismiss = onDismiss,
        title = "Change Password",
        confirmButton = {
            CentinelButton(
                text = "Update Password",
                onClick = {
                    errorMsg = null
                    if (oldPassword.isBlank()) {
                        errorMsg = "Please enter your current password."
                        return@CentinelButton
                    }
                    if (newPassword.length < 8) {
                        errorMsg = "New password must be at least 8 characters."
                        return@CentinelButton
                    }
                    if (newPassword != confirmPassword) {
                        errorMsg = "New passwords do not match."
                        return@CentinelButton
                    }
                    isLoading = true
                    onSubmit(oldPassword, newPassword) { err ->
                        isLoading = false
                        errorMsg = err
                    }
                },
                loading = isLoading,
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel", color = TextSecondary)
            }
        }
    ) {
        Column(modifier = Modifier.padding(top = 8.dp)) {
            if (errorMsg != null) {
                Surface(
                    color = DangerRed.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Text(
                        errorMsg!!,
                        color = DangerRed,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Column {
                Text("Current Password", style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it; errorMsg = null },
                    visualTransformation = if (oldVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TextTertiary) },
                    trailingIcon = {
                        IconButton(onClick = { oldVisible = !oldVisible }) {
                            Icon(
                                if (oldVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = TextTertiary
                            )
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = GlassBorder,
                        cursorColor = PrimaryBlue
                    )
                )
            }

            Spacer(Modifier.height(12.dp))

            Column {
                Text("New Password (min 8 chars)", style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; errorMsg = null },
                    visualTransformation = if (newVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = TextTertiary) },
                    trailingIcon = {
                        IconButton(onClick = { newVisible = !newVisible }) {
                            Icon(
                                if (newVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = TextTertiary
                            )
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = GlassBorder,
                        cursorColor = PrimaryBlue
                    )
                )
            }

            Spacer(Modifier.height(12.dp))

            Column {
                Text("Confirm New Password", style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; errorMsg = null },
                    visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TextTertiary) },
                    trailingIcon = {
                        IconButton(onClick = { confirmVisible = !confirmVisible }) {
                            Icon(
                                if (confirmVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = TextTertiary
                            )
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = GlassBorder,
                        cursorColor = PrimaryBlue
                    )
                )
            }
        }
    }
}

@Composable
fun NeuralFirewallDialog(
    settingsStore: SettingsStore,
    onDismiss: () -> Unit,
    onUpdated: () -> Unit
) {
    var realtime by remember { mutableStateOf(settingsStore.realtimeProtection) }
    var autoScan by remember { mutableStateOf(settingsStore.autoScanDownloads) }
    var antiPhishing by remember { mutableStateOf(settingsStore.antiPhishingShield) }

    CentinelDialog(
        onDismiss = onDismiss,
        title = "Neural Firewall",
        confirmButton = {
            CentinelButton(
                text = "Apply Settings",
                onClick = {
                    settingsStore.realtimeProtection = realtime
                    settingsStore.autoScanDownloads = autoScan
                    settingsStore.antiPhishingShield = antiPhishing
                    onUpdated()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        }
    ) {
        Column(modifier = Modifier.padding(top = 8.dp)) {
            Surface(
                color = PrimaryBlue.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("STATUS: ACTIVE", style = MaterialTheme.typography.labelSmall, color = AccentCyan, fontWeight = FontWeight.Bold)
                        Text("Protection Level: 100% Online", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    }
                }
            }

            SettingsToggleRow("Real-time Threat Interception", "Intercept malicious URLs & vectors live", realtime) { realtime = it }
            HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 4.dp))
            SettingsToggleRow("Auto-Scan Downloads", "Scan files as soon as they reach disk", autoScan) { autoScan = it }
            HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 4.dp))
            SettingsToggleRow("Anti-Phishing Link Shield", "Detect fraudulent login redirects", antiPhishing) { antiPhishing = it }
        }
    }
}

@Composable
fun PrivacyProtocolDialog(
    settingsStore: SettingsStore,
    onDismiss: () -> Unit,
    onClearCache: (Long) -> Unit
) {
    var zeroLog by remember { mutableStateOf(settingsStore.zeroLogRouting) }
    var encryptHistory by remember { mutableStateOf(settingsStore.encryptLocalHistory) }
    var anonymize by remember { mutableStateOf(settingsStore.anonymizeTelemetry) }

    CentinelDialog(
        onDismiss = onDismiss,
        title = "Privacy Protocol",
        confirmButton = {
            CentinelButton(
                text = "Save Preferences",
                onClick = {
                    settingsStore.zeroLogRouting = zeroLog
                    settingsStore.encryptLocalHistory = encryptHistory
                    settingsStore.anonymizeTelemetry = anonymize
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        }
    ) {
        Column(modifier = Modifier.padding(top = 8.dp)) {
            SettingsToggleRow("Zero-Log Network Routing", "No scan target logs kept on servers", zeroLog) { zeroLog = it }
            HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 4.dp))
            SettingsToggleRow("Encrypt Scan History", "Encrypt stored scan payloads with AES-256", encryptHistory) { encryptHistory = it }
            HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 4.dp))
            SettingsToggleRow("Anonymize Telemetry", "Strip user metadata from debug reports", anonymize) { anonymize = it }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    val freed = settingsStore.clearCache()
                    onClearCache(freed)
                },
                border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CleaningServices, contentDescription = null, tint = DangerRed, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Purge Temporary Scan Cache", color = DangerRed)
            }
        }
    }
}

@Composable
fun BiometricClearanceDialog(
    settingsStore: SettingsStore,
    onDismiss: () -> Unit,
    onTestBiometrics: () -> Unit
) {
    var enabled by remember { mutableStateOf(settingsStore.biometricEnabled) }

    CentinelDialog(
        onDismiss = onDismiss,
        title = "Biometric Clearance",
        confirmButton = {
            CentinelButton(
                text = "Save Settings",
                onClick = {
                    settingsStore.biometricEnabled = enabled
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        }
    ) {
        Column(modifier = Modifier.padding(top = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceVariant, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("BIOMETRIC SENSOR DETECTED", style = MaterialTheme.typography.labelSmall, color = AccentCyan, fontWeight = FontWeight.Bold)
                    Text("Fingerprint / Face Unlock Ready", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                }
            }

            Spacer(Modifier.height(12.dp))

            SettingsToggleRow("Require Clearance on Launch", "Lock terminal until biometric check passes", enabled) { enabled = it }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onTestBiometrics,
                border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Authenticate Test Scan", color = PrimaryBlue)
            }
        }
    }
}

@Composable
fun CommNotificationsDialog(
    settingsStore: SettingsStore,
    onDismiss: () -> Unit
) {
    var highRisk by remember { mutableStateOf(settingsStore.notifyHighRisk) }
    var dailyBriefs by remember { mutableStateOf(settingsStore.notifyDailyBriefs) }
    var summaryReports by remember { mutableStateOf(settingsStore.notifySummaryReports) }
    var soundVib by remember { mutableStateOf(settingsStore.notifySoundVibration) }

    CentinelDialog(
        onDismiss = onDismiss,
        title = "Comm Notifications",
        confirmButton = {
            CentinelButton(
                text = "Save Preferences",
                onClick = {
                    settingsStore.notifyHighRisk = highRisk
                    settingsStore.notifyDailyBriefs = dailyBriefs
                    settingsStore.notifySummaryReports = summaryReports
                    settingsStore.notifySoundVibration = soundVib
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        }
    ) {
        Column(modifier = Modifier.padding(top = 8.dp)) {
            SettingsToggleRow("High & Critical Threat Alerts", "Immediate pushes for dangerous threat detections", highRisk) { highRisk = it }
            HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 4.dp))
            SettingsToggleRow("Daily Intelligence Briefings", "Morning threat landscape summary", dailyBriefs) { dailyBriefs = it }
            HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 4.dp))
            SettingsToggleRow("Weekly Scan Summaries", "Aggregated risk and scan statistics", summaryReports) { summaryReports = it }
            HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 4.dp))
            SettingsToggleRow("Sound & Haptic Feedback", "Audible and vibration feedback on alerts", soundVib) { soundVib = it }
        }
    }
}

@Composable
fun VisualInterfaceDialog(
    settingsStore: SettingsStore,
    onDismiss: () -> Unit
) {
    val themes = listOf("Cyberpunk Dark", "Midnight OLED", "High Contrast Tactical")
    var selectedTheme by remember { mutableStateOf(settingsStore.themeMode) }
    var orbs by remember { mutableStateOf(settingsStore.backgroundOrbs) }

    CentinelDialog(
        onDismiss = onDismiss,
        title = "Visual Interface",
        confirmButton = {
            CentinelButton(
                text = "Apply Theme",
                onClick = {
                    settingsStore.themeMode = selectedTheme
                    settingsStore.backgroundOrbs = orbs
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        }
    ) {
        Column(modifier = Modifier.padding(top = 8.dp)) {
            Text("INTERFACE MODE", style = MaterialTheme.typography.labelSmall, color = AccentCyan, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            themes.forEach { theme ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedTheme = theme }
                        .padding(vertical = 6.dp)
                ) {
                    RadioButton(
                        selected = (theme == selectedTheme),
                        onClick = { selectedTheme = theme },
                        colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(theme, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = GlassBorder)
            Spacer(Modifier.height(12.dp))

            SettingsToggleRow("Background Glowing Orbs", "Render animated radial glow effects", orbs) { orbs = it }
        }
    }
}

@Composable
fun LanguageDecryptorDialog(
    settingsStore: SettingsStore,
    onDismiss: () -> Unit,
    onLanguageChanged: (String) -> Unit
) {
    val languages = listOf(
        "English (US)",
        "Spanish (Español)",
        "French (Français)",
        "German (Deutsch)",
        "Japanese (日本語)",
        "Cyber-Binary Mode"
    )
    var selectedLang by remember { mutableStateOf(settingsStore.appLanguage) }

    CentinelDialog(
        onDismiss = onDismiss,
        title = "Language Decryptor",
        confirmButton = {
            CentinelButton(
                text = "Select Language",
                onClick = {
                    settingsStore.appLanguage = selectedLang
                    onLanguageChanged(selectedLang)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        }
    ) {
        Column(modifier = Modifier.padding(top = 8.dp)) {
            languages.forEach { lang ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedLang = lang }
                        .padding(vertical = 6.dp)
                ) {
                    RadioButton(
                        selected = (lang == selectedLang),
                        onClick = { selectedLang = lang },
                        colors = RadioButtonDefaults.colors(selectedColor = AccentCyan)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(lang, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                }
            }
        }
    }
}

@Composable
fun AboutCentinelDialog(
    onDismiss: () -> Unit,
    onRunDiagnostics: () -> Unit
) {
    CentinelDialog(
        onDismiss = onDismiss,
        title = "About Centinel AI",
        confirmButton = {
            CentinelButton(
                text = "Run System Diagnostics",
                onClick = {
                    onRunDiagnostics()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        }
    ) {
        Column(modifier = Modifier.padding(top = 8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(listOf(PrimaryBlue.copy(alpha = 0.2f), Color.Transparent)),
                        RoundedCornerShape(16.dp)
                    )
                    .border(1.dp, PrimaryBlue.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("CENTINEL NEURAL KERNEL", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = TextPrimary)
                    Text("Version 2026.1.0 • Autonomous Intelligence", style = MaterialTheme.typography.bodySmall, color = AccentCyan)
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("System Architecture", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Centinel is an autonomous multi-threat cybersecurity engine combining heuristic risk analysis, NLP phishing detection, and real-time threat intelligence feeds.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun PrivacyManifestoDialog(onDismiss: () -> Unit) {
    CentinelDialog(
        onDismiss = onDismiss,
        title = "Privacy Manifesto",
        confirmButton = {
            CentinelButton("Acknowledge", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    ) {
        Column(modifier = Modifier.padding(top = 8.dp)) {
            Text(
                "1. Zero Knowledge: Your scanned URLs, SMS payloads, and file metadata are analyzed transiently and never sold.\n\n" +
                "2. Encrypted Storage: All scan history stored on-device or synced to backend uses Fernet symmetric encryption.\n\n" +
                "3. Full Sovereignty: You can purge your scan history or terminate your account permanently at any time.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun StandardOperationsDialog(onDismiss: () -> Unit) {
    CentinelDialog(
        onDismiss = onDismiss,
        title = "Standard Operations",
        confirmButton = {
            CentinelButton("Understood", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    ) {
        Column(modifier = Modifier.padding(top = 8.dp)) {
            Text(
                "Centinel AI provides predictive threat scoring based on confidence parameters. Always treat High and Critical threat indicators with extreme caution.\n\n" +
                "Do not interact with or click links inside flagged messages, files, or QR codes.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun KernelVersionDialog(onDismiss: () -> Unit) {
    CentinelDialog(
        onDismiss = onDismiss,
        title = "Kernel Diagnostics",
        confirmButton = {
            CentinelButton("Close", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    ) {
        Column(modifier = Modifier.padding(top = 8.dp)) {
            Text("Kernel Version: 2026.1.0-RELEASE", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Build Hash: 0x8F9A2C041E92", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text("Patch Level: August 2026", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text("Architecture: ARM64-v8a / Jetpack Compose", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text("Threat Engines: 10/10 Online", style = MaterialTheme.typography.bodySmall, color = AccentCyan)
        }
    }
}

// ==========================================
// Utility Components
// ==========================================

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryBlue,
                uncheckedThumbColor = TextTertiary,
                uncheckedTrackColor = SurfaceVariant
            )
        )
    }
}

@Composable
fun SettingsGroupRED(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = AccentCyan,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
        )
        CentinelGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
            Column(content = content)
        }
    }
}

@Composable
fun SettingsItemRED(
    icon: ImageVector,
    title: String,
    textColor: Color = TextPrimary,
    trailing: String? = null,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    if (textColor == DangerRed) DangerRed.copy(alpha = 0.1f) else SurfaceVariant,
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (textColor == DangerRed) DangerRed else PrimaryBlue,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Medium
        )
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                fontWeight = FontWeight.Bold
            )
        } else {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextTertiary.copy(alpha = 0.3f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
