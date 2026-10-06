package com.centinel.app.ui.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.centinel.app.CentinelConfig
import com.centinel.app.ui.common.ErrorBanner
import com.centinel.app.ui.common.UiState
import com.centinel.app.ui.components.*
import com.centinel.app.ui.theme.*

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoggedIn: () -> Unit,
    onNavigateToRegister: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showForgotDialog by remember { mutableStateOf(false) }
    var forgotEmail by remember { mutableStateOf("") }
    var showNetworkDialog by remember { mutableStateOf(false) }
    var networkUrl by remember { mutableStateOf(CentinelConfig.getBaseUrl()) }

    val state by viewModel.loginState.collectAsState()
    val forgotState by viewModel.forgotPasswordState.collectAsState()

    LaunchedEffect(state) {
        if (state is UiState.Success) {
            onLoggedIn()
            viewModel.resetLoginState()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        // Futuristic Background Visuals
        BackgroundOrbs()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(48.dp))
            
            // Redesigned Logo Visual
            Logo3D()
            
            Spacer(Modifier.height(32.dp))
            
            Text(
                "CENTINEL",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )
            Text(
                "ADVANCED AI CYBERSECURITY",
                style = MaterialTheme.typography.labelSmall,
                color = AccentCyan,
                letterSpacing = 2.sp
            )
            
            Spacer(Modifier.height(48.dp))

            CentinelGlassCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = PrimaryGlow.copy(alpha = 0.1f)
            ) {
                Text(
                    "Sign In",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(24.dp))

                CentinelTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Email Address",
                    icon = Icons.Default.Email,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
                )
                
                Spacer(Modifier.height(20.dp))
                
                CentinelTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    icon = Icons.Default.Lock,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password)
                )
                
                if (state is UiState.Error) {
                    Spacer(Modifier.height(16.dp))
                    ErrorBanner((state as UiState.Error).message)
                }
                
                Spacer(Modifier.height(32.dp))

                CentinelButton(
                    "Authorize Access",
                    loading = state is UiState.Loading,
                    enabled = email.isNotBlank() && password.isNotBlank(),
                    onClick = { viewModel.login(email.trim(), password) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                CentinelButton(
                    "Enter Admin Test Mode",
                    onClick = {
                        CentinelConfig.isAdminTestMode = true
                        onLoggedIn()
                    },
                    colors = CyanGradient,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Terminal
                )

                Spacer(Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { showNetworkDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, GlassBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Icon(Icons.Default.SettingsEthernet, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Configure Network Protocol", style = MaterialTheme.typography.labelLarge)
                }

                Spacer(Modifier.height(16.dp))

                TextButton(
                    onClick = { showForgotDialog = true },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Forgot Password?", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
                }
            }
            
            Spacer(Modifier.height(32.dp))
            
            TextButton(onClick = onNavigateToRegister) {
                Text(
                    "New Commander? Request Access",
                    color = AccentCyan,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(48.dp))
        }

        if (showNetworkDialog) {
            CentinelDialog(
                onDismiss = { showNetworkDialog = false },
                title = "Network Configuration",
                confirmButton = {
                    CentinelButton(
                        "Apply & Sync",
                        onClick = {
                            CentinelConfig.customApiUrl = networkUrl.trim().let { 
                                if (it.endsWith("/")) it else "$it/"
                            }
                            showNetworkDialog = false
                        },
                        colors = CyanGradient
                    )
                },
                dismissButton = {
                    TextButton(onClick = { showNetworkDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            ) {
                Column {
                    Text(
                        "Current Base URL: ${CentinelConfig.getBaseUrl()}",
                        color = TextTertiary,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(Modifier.height(16.dp))
                    CentinelTextField(
                        value = networkUrl,
                        onValueChange = { networkUrl = it },
                        label = "API Endpoint (e.g. http://10.0.0.1:8000/)",
                        icon = Icons.Default.Link
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Note: Ensure your device can reach this address over the current network interface.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (showForgotDialog) {
            CentinelDialog(
                onDismiss = {
                    showForgotDialog = false
                    viewModel.resetForgotPasswordState()
                },
                title = "Reset Credentials",
                confirmButton = {
                    CentinelButton(
                        "Send Protocol",
                        onClick = { viewModel.forgotPassword(forgotEmail.trim()) },
                        loading = forgotState is UiState.Loading,
                        enabled = forgotEmail.isNotBlank(),
                        colors = CyanGradient
                    )
                },
                dismissButton = {
                    TextButton(onClick = { showForgotDialog = false }) {
                        Text("Abort", color = TextSecondary)
                    }
                }
            ) {
                Column {
                    Text(
                        "Enter the registered email to initiate recovery protocol.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(20.dp))
                    CentinelTextField(
                        value = forgotEmail,
                        onValueChange = { forgotEmail = it },
                        label = "Email Address",
                        icon = Icons.Default.Email
                    )
                    if (forgotState is UiState.Error) {
                        Spacer(Modifier.height(8.dp))
                        Text((forgotState as UiState.Error).message, color = DangerRed, style = MaterialTheme.typography.bodySmall)
                    }
                    if (forgotState is UiState.Success) {
                        Spacer(Modifier.height(8.dp))
                        Text("Recovery link dispatched.", color = SuccessGreen, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun Logo3D() {
    val infiniteTransition = rememberInfiniteTransition(label = "logo")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(15000, easing = LinearEasing)),
        label = "rotation"
    )

    Box(contentAlignment = Alignment.Center) {
        // Glowing background
        Box(modifier = Modifier.size(120.dp).background(Brush.radialGradient(listOf(GlowBlue, Color.Transparent)), CircleShape))
        
        // Rotating Ring 1
        Box(modifier = Modifier.size(100.dp).rotate(rotation).border(2.dp, Brush.sweepGradient(PremiumGradient), CircleShape))
        
        // Rotating Ring 2
        Box(modifier = Modifier.size(80.dp).rotate(-rotation * 2).border(1.5.dp, Brush.sweepGradient(CyanGradient), CircleShape))
        
        // Central Core
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(PremiumGradient))
                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Security, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
fun BackgroundOrbs() {
    val infiniteTransition = rememberInfiniteTransition(label = "orbs")
    val xOffset by infiniteTransition.animateFloat(
        initialValue = -50f,
        targetValue = 50f,
        animationSpec = infiniteRepeatable(tween(8000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "x"
    )
    val yOffset by infiniteTransition.animateFloat(
        initialValue = -30f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(tween(6000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "y"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(400.dp)
                .offset(x = (xOffset - 100).dp, y = (yOffset - 100).dp)
                .background(Brush.radialGradient(listOf(GlowBlue.copy(alpha = 0.2f), Color.Transparent)), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomEnd)
                .offset(x = (50 - xOffset).dp, y = (30 - yOffset).dp)
                .background(Brush.radialGradient(listOf(GlowPurple.copy(alpha = 0.15f), Color.Transparent)), CircleShape)
        )
    }
}

