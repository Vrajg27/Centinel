package com.centinel.app.ui.auth

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.centinel.app.ui.common.ErrorBanner
import com.centinel.app.ui.common.UiState
import com.centinel.app.ui.components.*
import com.centinel.app.ui.theme.*

@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onRegistered: () -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val state by viewModel.registerState.collectAsState()

    LaunchedEffect(state) {
        if (state is UiState.Success) {
            onRegistered()
            viewModel.resetRegisterState()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
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
            Logo3D()
            Spacer(Modifier.height(32.dp))
            
            Text(
                "ENLIST",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )
            Text(
                "JOIN THE CENTINEL INTELLIGENCE NETWORK",
                style = MaterialTheme.typography.labelSmall,
                color = AccentCyan,
                letterSpacing = 1.sp
            )
            
            Spacer(Modifier.height(48.dp))

            CentinelGlassCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = GlowPurple.copy(alpha = 0.1f)
            ) {
                Text(
                    "Registration Protocol",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(24.dp))

                CentinelTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = "Full Name",
                    icon = Icons.Default.Person
                )
                
                Spacer(Modifier.height(20.dp))

                CentinelTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Security Email",
                    icon = Icons.Default.Email,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
                )
                
                Spacer(Modifier.height(20.dp))
                
                CentinelTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Access Password",
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
                    "Authorize Protocol",
                    loading = state is UiState.Loading,
                    enabled = email.isNotBlank() && password.isNotBlank() && fullName.isNotBlank(),
                    onClick = { viewModel.register(email.trim(), password, fullName.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = listOf(SecondaryPurple, PrimaryBlue)
                )
            }
            
            Spacer(Modifier.height(32.dp))
            
            TextButton(onClick = onNavigateToLogin) {
                Text(
                    "Already have clearance? Access Terminal",
                    color = AccentCyan,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}
