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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.di.ViewModelFactory
import com.centinel.app.ui.components.*
import com.centinel.app.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun BiometricClearanceScreen(
    repo: CentinelRepository,
    onBack: () -> Unit
) {
    val vm: SettingsViewModel = viewModel(factory = ViewModelFactory(repo))
    val settingsStore = vm.settingsStore

    var enabled by remember { mutableStateOf(settingsStore.biometricEnabled) }
    var isAuthenticating by remember { mutableStateOf(false) }

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
                    "Biometric Clearance Interface",
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
                // Biometric Graphic Hero Card
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
                                .size(88.dp)
                                .background(PrimaryBlue.copy(alpha = 0.15f), CircleShape)
                                .border(2.dp, AccentCyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(52.dp)
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            "BIOMETRIC HARDWARE READY",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentCyan,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )

                        Text(
                            "Fingerprint / Face ID Protection",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            "Secure terminal access with hardware-backed biometric verification.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Switches
                Text(
                    "BIOMETRIC CLEARANCE CONTROLS",
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
                    SettingsToggleRow(
                        title = "Require Biometric Clearance on Launch",
                        subtitle = "Locks terminal until fingerprint/Face ID is verified",
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            settingsStore.biometricEnabled = it
                            scope.launch {
                                snackbarHostState.showSnackbar(if (it) "Biometric clearance enabled." else "Biometric clearance disabled.")
                            }
                        }
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Test Scan Button
                OutlinedButton(
                    onClick = {
                        isAuthenticating = true
                        scope.launch {
                            delay(800)
                            isAuthenticating = false
                            snackbarHostState.showSnackbar("Biometric clearance verified. Sensor hardware active.")
                        }
                    },
                    border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    if (isAuthenticating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = PrimaryBlue, strokeWidth = 2.dp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Authenticate Test Biometric Scan", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                CentinelButton(
                    text = "Save Biometric Settings",
                    onClick = {
                        settingsStore.biometricEnabled = enabled
                        scope.launch {
                            snackbarHostState.showSnackbar("Biometric preferences saved.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Save
                )

                Spacer(Modifier.height(48.dp))
            }
        }
    }
}
