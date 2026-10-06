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

@Composable
fun PrivacyProtocolScreen(
    repo: CentinelRepository,
    onBack: () -> Unit
) {
    val vm: SettingsViewModel = viewModel(factory = ViewModelFactory(repo))
    val settingsStore = vm.settingsStore

    var zeroLog by remember { mutableStateOf(settingsStore.zeroLogRouting) }
    var encryptHistory by remember { mutableStateOf(settingsStore.encryptLocalHistory) }
    var anonymize by remember { mutableStateOf(settingsStore.anonymizeTelemetry) }

    var cacheFreedMb by remember { mutableStateOf<Long?>(null) }

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
                    "Privacy Protocol Interface",
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
                    .background(Brush.radialGradient(listOf(GlowPurple.copy(alpha = 0.15f), Color.Transparent)), CircleShape)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // Privacy Hero Card
                CentinelGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = PrimaryGlow.copy(alpha = 0.2f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    Brush.linearGradient(listOf(AccentCyan.copy(alpha = 0.2f), Color.Transparent)),
                                    RoundedCornerShape(16.dp)
                                )
                                .border(1.dp, AccentCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("ZERO-KNOWLEDGE PRIVACY GUARANTEE", style = MaterialTheme.typography.labelSmall, color = AccentCyan, fontWeight = FontWeight.Bold)
                            Text("Data Isolation Protocols", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text("Your scan targets are processed transiently with zero persistent logging.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Switches List
                Text(
                    "PRIVACY CONTROLS",
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
                            title = "Zero-Log Network Routing",
                            subtitle = "No scan target logs or request payloads are kept on servers",
                            checked = zeroLog,
                            onCheckedChange = {
                                zeroLog = it
                                settingsStore.zeroLogRouting = it
                            }
                        )

                        HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 4.dp))

                        SettingsToggleRow(
                            title = "Encrypt Scan History Payloads",
                            subtitle = "Encrypt stored scan history with AES-256 Fernet keys",
                            checked = encryptHistory,
                            onCheckedChange = {
                                encryptHistory = it
                                settingsStore.encryptLocalHistory = it
                            }
                        )

                        HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 4.dp))

                        SettingsToggleRow(
                            title = "Anonymize Telemetry Data",
                            subtitle = "Strip hardware device identifiers from crash reports",
                            checked = anonymize,
                            onCheckedChange = {
                                anonymize = it
                                settingsStore.anonymizeTelemetry = it
                            }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Cache Purge Card
                Text(
                    "DATA STORAGE & CACHE CLEANSING",
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text("Temporary Scan Artifacts", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (cacheFreedMb == null) "28.4 MB cached scan payloads" else "Cache Purged (0 MB remaining)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }

                            Icon(Icons.Default.CleaningServices, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(24.dp))
                        }

                        Spacer(Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = {
                                val freed = settingsStore.clearCache()
                                val mb = freed / (1024 * 1024)
                                cacheFreedMb = mb
                                scope.launch {
                                    snackbarHostState.showSnackbar("Purged $mb MB of temporary scan artifacts.")
                                }
                            },
                            border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = DangerRed, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Purge Local Scan Artifacts", color = DangerRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                CentinelButton(
                    text = "Save Privacy Preferences",
                    onClick = {
                        settingsStore.zeroLogRouting = zeroLog
                        settingsStore.encryptLocalHistory = encryptHistory
                        settingsStore.anonymizeTelemetry = anonymize
                        scope.launch {
                            snackbarHostState.showSnackbar("Privacy protocol saved.")
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
