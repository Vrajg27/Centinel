package com.centinel.app.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.di.ViewModelFactory
import com.centinel.app.ui.common.UiState
import com.centinel.app.ui.components.*
import com.centinel.app.ui.theme.*

@Composable
fun ProfileDetailsScreen(
    repo: CentinelRepository,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val vm: SettingsViewModel = viewModel(factory = ViewModelFactory(repo))
    val userState by vm.userState.collectAsState()

    var fullNameInput by remember { mutableStateOf("") }
    var isInitialized by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        vm.loadUser()
    }

    val user = (userState as? UiState.Success)?.data

    LaunchedEffect(user) {
        if (user != null && !isInitialized) {
            fullNameInput = user.full_name ?: ""
            isInitialized = true
        }
    }

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
                    modifier = Modifier
                        .background(SurfaceVariant, CircleShape)
                        .border(1.dp, GlassBorder, CircleShape)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    "Profile Clearance Interface",
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
            // Radial Glow Background
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 100.dp, y = (-50).dp)
                    .background(
                        Brush.radialGradient(listOf(GlowBlue.copy(alpha = 0.15f), Color.Transparent)),
                        CircleShape
                    )
            )

            if (userState is UiState.Loading && user == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentCyan, strokeWidth = 3.dp)
                }
            } else if (user != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    // Main Clearance Header Card
                    CentinelGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = PrimaryGlow.copy(alpha = 0.2f)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(PremiumGradient))
                                    .padding(3.dp)
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
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            Text(
                                user.full_name?.ifBlank { null } ?: "Commander Centinel",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                user.email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                letterSpacing = 0.5.sp
                            )

                            Spacer(Modifier.height(12.dp))

                            Surface(
                                color = if (user.is_admin) PrimaryBlue.copy(alpha = 0.15f) else AccentCyan.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (user.is_admin) PrimaryBlue.copy(alpha = 0.3f) else AccentCyan.copy(alpha = 0.3f)
                                )
                            ) {
                                Text(
                                    if (user.is_admin) "LEVEL 5 ADMIN CLEARANCE" else "SECURITY CLEARANCE: COMMANDER",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = if (user.is_admin) PrimaryBlue else AccentCyan,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.2.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Account Information Card
                    Text(
                        "ACCOUNT IDENTITY",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                    )

                    CentinelGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 20.dp
                    ) {
                        Column {
                            CentinelTextField(
                                value = fullNameInput,
                                onValueChange = { fullNameInput = it },
                                label = "Full Name",
                                icon = Icons.Default.Person,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(16.dp))

                            CentinelTextField(
                                value = user.email,
                                onValueChange = {},
                                label = "Registered Email Address (Locked)",
                                icon = Icons.Default.Email,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(16.dp))

                            Column {
                                Text(
                                    "User Identifier (UID)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                                )
                                Surface(
                                    color = SurfaceVariant,
                                    shape = RoundedCornerShape(18.dp),
                                    border = BorderStroke(1.dp, GlassBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Fingerprint,
                                                contentDescription = null,
                                                tint = PrimaryBlue,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                                user.id.take(18) + "...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(user.id))
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("User ID copied to clipboard.")
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = "Copy ID",
                                                tint = AccentCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Security & Clearance Stats
                    Text(
                        "SECURITY MATRIX",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CentinelStatCard(
                            label = "Security Rank",
                            value = if (user.is_admin) "L5 Admin" else "Commander",
                            icon = Icons.Default.Shield,
                            color = PrimaryBlue,
                            modifier = Modifier.weight(1f)
                        )

                        CentinelStatCard(
                            label = "Terminal Link",
                            value = "Encrypted",
                            icon = Icons.Default.Lock,
                            color = SuccessGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(32.dp))

                    // Actions
                    CentinelButton(
                        text = "Save Profile Details",
                        onClick = {
                            isSaving = true
                            vm.updateProfile(
                                fullName = fullNameInput.trim(),
                                onSuccess = {
                                    isSaving = false
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Profile details saved successfully.")
                                    }
                                },
                                onError = { err ->
                                    isSaving = false
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Failed to update profile: $err")
                                    }
                                }
                            )
                        },
                        loading = isSaving,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.Save
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { showChangePasswordDialog = true },
                        border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(
                            Icons.Default.Password,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Change Password",
                            color = PrimaryBlue,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    CentinelButton(
                        text = "Sign Out Of Terminal",
                        onClick = {
                            vm.logout(onLogout)
                        },
                        colors = listOf(DangerRed, Color(0xFF880000)),
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.AutoMirrored.Filled.Logout
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = DangerRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Terminate Account",
                            color = DangerRed,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(48.dp))
                }
            }
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
                            scope.launch {
                                snackbarHostState.showSnackbar("Password updated successfully.")
                            }
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
    }
}
