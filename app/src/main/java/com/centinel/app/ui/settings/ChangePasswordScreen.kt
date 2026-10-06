package com.centinel.app.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.di.ViewModelFactory
import com.centinel.app.ui.components.*
import com.centinel.app.ui.theme.*

@Composable
fun ChangePasswordScreen(
    repo: CentinelRepository,
    onBack: () -> Unit
) {
    val vm: SettingsViewModel = viewModel(factory = ViewModelFactory(repo))

    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var oldVisible by remember { mutableStateOf(false) }
    var newVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }

    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    "Password Security Interface",
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
                    .align(Alignment.TopEnd)
                    .offset(x = 100.dp, y = (-50).dp)
                    .background(Brush.radialGradient(listOf(GlowBlue.copy(alpha = 0.15f), Color.Transparent)), CircleShape)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // Key Hero Card
                CentinelGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = PrimaryGlow.copy(alpha = 0.2f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    Brush.linearGradient(listOf(PrimaryBlue.copy(alpha = 0.2f), Color.Transparent)),
                                    RoundedCornerShape(16.dp)
                                )
                                .border(1.dp, PrimaryBlue.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.LockReset, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("UPDATE ACCESS CREDENTIALS", style = MaterialTheme.typography.labelSmall, color = AccentCyan, fontWeight = FontWeight.Bold)
                            Text("Change Terminal Password", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text("Secure your terminal with a strong multi-character passphrase.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                if (errorMsg != null) {
                    Surface(
                        color = DangerRed.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = DangerRed, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(errorMsg!!, color = DangerRed, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Password Form Card
                CentinelGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp
                ) {
                    Column {
                        // Current Password
                        Column {
                            Text("Current Password", style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                            OutlinedTextField(
                                value = oldPassword,
                                onValueChange = { oldPassword = it; errorMsg = null },
                                visualTransformation = if (oldVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TextTertiary) },
                                trailingIcon = {
                                    IconButton(onClick = { oldVisible = !oldVisible }) {
                                        Icon(if (oldVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, tint = TextTertiary)
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

                        Spacer(Modifier.height(16.dp))

                        // New Password
                        Column {
                            Text("New Password (Minimum 8 Characters)", style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                            OutlinedTextField(
                                value = newPassword,
                                onValueChange = { newPassword = it; errorMsg = null },
                                visualTransformation = if (newVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = TextTertiary) },
                                trailingIcon = {
                                    IconButton(onClick = { newVisible = !newVisible }) {
                                        Icon(if (newVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, tint = TextTertiary)
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

                        Spacer(Modifier.height(16.dp))

                        // Confirm New Password
                        Column {
                            Text("Confirm New Password", style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it; errorMsg = null },
                                visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TextTertiary) },
                                trailingIcon = {
                                    IconButton(onClick = { confirmVisible = !confirmVisible }) {
                                        Icon(if (confirmVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, tint = TextTertiary)
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

                Spacer(Modifier.height(24.dp))

                // Security Policy Card
                Surface(
                    color = SurfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, GlassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("PASSWORD SECURITY REQUIREMENTS", style = MaterialTheme.typography.labelSmall, color = AccentCyan, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("• Passwords must be at least 8 characters in length.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text("• Combine uppercase letters, digits, and special symbols.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text("• Avoid easily guessable phrases or dictionary words.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Action Button
                CentinelButton(
                    text = "Update Password Protocol",
                    onClick = {
                        errorMsg = null
                        if (oldPassword.isBlank()) {
                            errorMsg = "Please enter your current password."
                            return@CentinelButton
                        }
                        if (newPassword.length < 8) {
                            errorMsg = "New password must be at least 8 characters long."
                            return@CentinelButton
                        }
                        if (newPassword != confirmPassword) {
                            errorMsg = "New passwords do not match."
                            return@CentinelButton
                        }
                        isLoading = true
                        vm.changePassword(
                            oldPass = oldPassword,
                            newPass = newPassword,
                            onSuccess = {
                                isLoading = false
                                scope.launch {
                                    snackbarHostState.showSnackbar("Password updated successfully.")
                                }
                                oldPassword = ""
                                newPassword = ""
                                confirmPassword = ""
                            },
                            onError = { err ->
                                isLoading = false
                                errorMsg = err
                            }
                        )
                    },
                    loading = isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Save
                )

                Spacer(Modifier.height(48.dp))
            }
        }
    }
}
