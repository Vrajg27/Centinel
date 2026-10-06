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
fun CommNotificationsScreen(
    repo: CentinelRepository,
    onBack: () -> Unit
) {
    val vm: SettingsViewModel = viewModel(factory = ViewModelFactory(repo))
    val settingsStore = vm.settingsStore

    var highRisk by remember { mutableStateOf(settingsStore.notifyHighRisk) }
    var dailyBriefs by remember { mutableStateOf(settingsStore.notifyDailyBriefs) }
    var summaryReports by remember { mutableStateOf(settingsStore.notifySummaryReports) }
    var soundVib by remember { mutableStateOf(settingsStore.notifySoundVibration) }

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
                    "Comm Notifications Interface",
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
                // Bell Hero Card
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
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("PUSH NOTIFICATION PREFERENCES", style = MaterialTheme.typography.labelSmall, color = AccentCyan, fontWeight = FontWeight.Bold)
                            Text("Threat Intelligence Channels", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text("Configure real-time push alerts and daily threat dispatches.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Switches
                Text(
                    "NOTIFICATION CHANNELS",
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
                            title = "High & Critical Threat Alerts",
                            subtitle = "Immediate pushes for dangerous threat detections",
                            checked = highRisk,
                            onCheckedChange = {
                                highRisk = it
                                settingsStore.notifyHighRisk = it
                            }
                        )

                        HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 4.dp))

                        SettingsToggleRow(
                            title = "Daily Threat Intelligence Briefings",
                            subtitle = "Morning security summary of detected threat vectors",
                            checked = dailyBriefs,
                            onCheckedChange = {
                                dailyBriefs = it
                                settingsStore.notifyDailyBriefs = it
                            }
                        )

                        HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 4.dp))

                        SettingsToggleRow(
                            title = "Weekly Scan Summaries",
                            subtitle = "Aggregated weekly risk metrics and scan performance reports",
                            checked = summaryReports,
                            onCheckedChange = {
                                summaryReports = it
                                settingsStore.notifySummaryReports = it
                            }
                        )

                        HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 4.dp))

                        SettingsToggleRow(
                            title = "Sound & Haptic Feedback",
                            subtitle = "Audible chime and vibration feedback on alert delivery",
                            checked = soundVib,
                            onCheckedChange = {
                                soundVib = it
                                settingsStore.notifySoundVibration = it
                            }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // FCM Push Channel Status Card
                Surface(
                    color = SurfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, GlassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("PUSH DISPATCH LINK: CONNECTED", style = MaterialTheme.typography.labelSmall, color = AccentCyan, fontWeight = FontWeight.Bold)
                            Text("Firebase Cloud Messaging active on Android.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                CentinelButton(
                    text = "Save Notification Preferences",
                    onClick = {
                        settingsStore.notifyHighRisk = highRisk
                        settingsStore.notifyDailyBriefs = dailyBriefs
                        settingsStore.notifySummaryReports = summaryReports
                        settingsStore.notifySoundVibration = soundVib
                        scope.launch {
                            snackbarHostState.showSnackbar("Notification preferences saved.")
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
