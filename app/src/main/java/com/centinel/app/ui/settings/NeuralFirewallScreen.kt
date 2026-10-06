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
import com.centinel.app.ui.dashboard.SecurityOrb3D
import com.centinel.app.ui.theme.*

@Composable
fun NeuralFirewallScreen(
    repo: CentinelRepository,
    onBack: () -> Unit
) {
    val vm: SettingsViewModel = viewModel(factory = ViewModelFactory(repo))
    val settingsStore = vm.settingsStore

    var realtime by remember { mutableStateOf(settingsStore.realtimeProtection) }
    var autoScan by remember { mutableStateOf(settingsStore.autoScanDownloads) }
    var antiPhishing by remember { mutableStateOf(settingsStore.antiPhishingShield) }
    var firewallActive by remember { mutableStateOf(settingsStore.firewallEnabled) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val isProtectionActive = firewallActive && realtime

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
                    "Neural Firewall Dashboard",
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
                    .background(
                        Brush.radialGradient(listOf(if (isProtectionActive) AccentCyanGlow else DangerRed.copy(alpha = 0.2f), Color.Transparent)),
                        CircleShape
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // Orb & Status Hero Card
                CentinelGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = if (isProtectionActive) PrimaryGlow.copy(alpha = 0.25f) else DangerRed.copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1.2f)) {
                            Surface(
                                color = if (isProtectionActive) SuccessGreen.copy(alpha = 0.15f) else DangerRed.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isProtectionActive) SuccessGreen.copy(alpha = 0.3f) else DangerRed.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    if (isProtectionActive) "FIREWALL ACTIVE" else "PROTECTION PAUSED",
                                    color = if (isProtectionActive) SuccessGreen else DangerRed,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            Text("Protection Level", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text(
                                if (isProtectionActive) "100% ONLINE" else "0% DEACTIVATED",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = if (isProtectionActive) AccentCyan else DangerRed
                            )

                            Spacer(Modifier.height(8.dp))

                            Text(
                                if (isProtectionActive) "Intercepting real-time vectors." else "Enable switches below to resume firewall.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                        }

                        Box(modifier = Modifier.weight(0.8f), contentAlignment = Alignment.Center) {
                            SecurityOrb3D(settingsStore)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Master Toggle
                CentinelGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp
                ) {
                    SettingsToggleRow(
                        title = "Master Neural Firewall Switch",
                        subtitle = "Global toggle for all active protection modules",
                        checked = firewallActive,
                        onCheckedChange = {
                            firewallActive = it
                            settingsStore.firewallEnabled = it
                            scope.launch {
                                snackbarHostState.showSnackbar(if (it) "Neural Firewall activated." else "Neural Firewall deactivated.")
                            }
                        }
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Module Switches
                Text(
                    "PROTECTION MODULES",
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
                        SettingsToggleRow(
                            title = "Real-Time Threat Interception",
                            subtitle = "Intercept malicious URLs, SMS, and vectors live",
                            checked = realtime,
                            onCheckedChange = {
                                realtime = it
                                settingsStore.realtimeProtection = it
                            }
                        )

                        HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 4.dp))

                        SettingsToggleRow(
                            title = "Auto-Scan Downloads",
                            subtitle = "Scan incoming files as soon as they reach disk",
                            checked = autoScan,
                            onCheckedChange = {
                                autoScan = it
                                settingsStore.autoScanDownloads = it
                            }
                        )

                        HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 4.dp))

                        SettingsToggleRow(
                            title = "Anti-Phishing Link Shield",
                            subtitle = "Detect fraudulent login redirects and credential harvesters",
                            checked = antiPhishing,
                            onCheckedChange = {
                                antiPhishing = it
                                settingsStore.antiPhishingShield = it
                            }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Firewall Stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CentinelStatCard(
                        label = "Threats Blocked",
                        value = "42",
                        icon = Icons.Default.ShieldMoon,
                        color = PrimaryBlue,
                        modifier = Modifier.weight(1f)
                    )

                    CentinelStatCard(
                        label = "Clean Scans",
                        value = "1,280",
                        icon = Icons.Default.VerifiedUser,
                        color = SuccessGreen,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(32.dp))

                CentinelButton(
                    text = "Apply Firewall Configuration",
                    onClick = {
                        settingsStore.firewallEnabled = firewallActive
                        settingsStore.realtimeProtection = realtime
                        settingsStore.autoScanDownloads = autoScan
                        settingsStore.antiPhishingShield = antiPhishing
                        scope.launch {
                            snackbarHostState.showSnackbar("Firewall preferences saved.")
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
