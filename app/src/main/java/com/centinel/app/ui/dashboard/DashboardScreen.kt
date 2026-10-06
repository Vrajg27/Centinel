package com.centinel.app.ui.dashboard

import android.Manifest
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.centinel.app.data.local.SettingsStore
import com.centinel.app.data.model.AnalyticsOut
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.di.ViewModelFactory
import com.centinel.app.ui.common.ErrorBanner
import com.centinel.app.ui.common.UiState
import com.centinel.app.ui.components.*
import com.centinel.app.ui.navigation.Screen
import com.centinel.app.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

data class ToolCardData(val title: String, val icon: ImageVector, val route: String)

val TOOLS = listOf(
    ToolCardData("URL Scanner", Icons.Filled.Link, Screen.UrlScanner.route),
    ToolCardData("Email Scanner", Icons.Filled.Email, Screen.EmailScanner.route),
    ToolCardData("Header Analyzer", Icons.Filled.Dns, Screen.HeaderAnalyzer.route),
    ToolCardData("SMS Scanner", Icons.Filled.Sms, Screen.SmsScanner.route),
    ToolCardData("QR Scanner", Icons.Filled.QrCodeScanner, Screen.QrScanner.route),
    ToolCardData("File Scanner", Icons.Filled.UploadFile, Screen.FileScanner.route),
    ToolCardData("Password Analyzer", Icons.Filled.Password, Screen.PasswordAnalyzer.route),
    ToolCardData("Scan History", Icons.Filled.History, Screen.History.route),
    ToolCardData("Threat Analytics", Icons.Filled.BarChart, Screen.Analytics.route),
    ToolCardData("Notifications", Icons.Filled.Notifications, Screen.Notifications.route),
)

@OptIn(ExperimentalPermissionsApi::class, ExperimentalAnimationApi::class)
@Composable
fun DashboardScreen(repo: CentinelRepository, onNavigate: (String) -> Unit) {
    val vm: DashboardViewModel = viewModel(factory = ViewModelFactory(repo))
    val analytics by vm.analyticsState.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    val settingsStore = repo.settingsStore

    LaunchedEffect(Unit) { vm.loadAnalytics() }

    if (Build.VERSION.SDK_INT >= 33) {
        val notificationPermission = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
        LaunchedEffect(Unit) {
            if (!notificationPermission.status.isGranted) {
                notificationPermission.launchPermissionRequest()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val gridStep = 40.dp.toPx()
                    for (x in 0..(size.width / gridStep).toInt()) {
                        drawLine(
                            Color.White.copy(alpha = 0.03f),
                            Offset(x * gridStep, 0f),
                            Offset(x * gridStep, size.height)
                        )
                    }
                    for (y in 0..(size.height / gridStep).toInt()) {
                        drawLine(
                            Color.White.copy(alpha = 0.03f),
                            Offset(0f, y * gridStep),
                            Offset(size.width, y * gridStep)
                        )
                    }
                }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                PremiumHeaderRedesigned(onNavigate)
            }

            item {
                HeroSectionRED(settingsStore)
                Spacer(Modifier.height(24.dp))
            }

            if (!settingsStore.realtimeProtection || !settingsStore.firewallEnabled) {
                item {
                    Surface(
                        color = DangerRed.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .clickable { onNavigate(Screen.Settings.route) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = DangerRed, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("NEURAL FIREWALL DEACTIVATED", style = MaterialTheme.typography.titleSmall, color = DangerRed, fontWeight = FontWeight.Bold)
                                Text("Real-time protection is paused in settings. Tap to activate.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }

            item {
                if (analytics != null) {
                    QuickStatsRowRED(analytics!!)
                } else if (isRefreshing) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentCyan, strokeWidth = 3.dp)
                    }
                } else {
                    ErrorBanner(
                        message = "Protocol Error: Unable to fetch live analytics.",
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
                Spacer(Modifier.height(40.dp))
            }

            item {
                Text(
                    "Security Command Center",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(Modifier.height(20.dp))
            }

            items(TOOLS, key = { it.route }) { tool ->
                CentinelToolCard(
                    title = tool.title,
                    subtitle = "Secure your ${tool.title.lowercase()}",
                    icon = tool.icon,
                    onClick = { onNavigate(tool.route) },
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun PremiumHeaderRedesigned(onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(PremiumGradient))
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(SurfaceVariant)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center).size(28.dp),
                        tint = TextPrimary
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    "Welcome Commander,",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
                Text(
                    "Centinel User",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Row {
            HeaderIconButton(Icons.Default.Notifications) { onNavigate(Screen.Notifications.route) }
            Spacer(Modifier.width(12.dp))
            HeaderIconButton(Icons.Default.Settings) { onNavigate(Screen.Settings.route) }
        }
    }
}

@Composable
fun HeaderIconButton(icon: ImageVector, onClick: () -> Unit) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
            .border(1.2.dp, primaryColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = primaryColor, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun HeroSectionRED(settingsStore: SettingsStore) {
    val isFirewallActive = settingsStore.realtimeProtection && settingsStore.firewallEnabled

    CentinelGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(240.dp),
        glowColor = if (isFirewallActive) AccentCyanGlow else DangerRed.copy(alpha = 0.2f)
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1.2f)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isFirewallActive) SuccessGreen.copy(alpha = 0.15f) else DangerRed.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (isFirewallActive) "SYSTEM ACTIVE" else "FIREWALL PAUSED",
                        color = if (isFirewallActive) SuccessGreen else DangerRed,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("Security Score", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                AnimatedCounter(
                    targetValue = if (isFirewallActive) 98 else 65,
                    suffix = "%",
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 48.sp, fontWeight = FontWeight.Black),
                    color = if (isFirewallActive) TextPrimary else DangerRed
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isFirewallActive) "Your device is protected by 2026 Centinel AI." else "Protection paused. Turn on Neural Firewall in settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
            
            Box(modifier = Modifier.weight(0.8f), contentAlignment = Alignment.Center) {
                SecurityOrb3D(settingsStore)
            }
        }
    }
}

@Composable
fun SecurityOrb3D(settingsStore: SettingsStore) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing)),
        label = "rotation"
    )
    
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    val isFirewallActive = settingsStore.realtimeProtection && settingsStore.firewallEnabled

    Box(contentAlignment = Alignment.Center) {
        if (settingsStore.backgroundOrbs) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                        alpha = 0.6f
                    }
                    .background(Brush.radialGradient(listOf(if (isFirewallActive) PrimaryGlow else DangerRed.copy(alpha = 0.3f), Color.Transparent)), CircleShape)
            )
        }
        
        Canvas(
            modifier = Modifier
                .size(100.dp)
                .graphicsLayer { rotationZ = rotation }
        ) {
            drawCircle(
                brush = Brush.sweepGradient(if (isFirewallActive) PremiumGradient else listOf(DangerRed, Color.DarkGray, DangerRed)),
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                alpha = 0.8f
            )
        }
        
        Canvas(
            modifier = Modifier
                .size(70.dp)
                .graphicsLayer { rotationZ = -rotation * 1.5f }
        ) {
            drawCircle(
                brush = Brush.sweepGradient(listOf(if (isFirewallActive) AccentCyan else DangerRed, Color.Transparent, if (isFirewallActive) AccentCyan else DangerRed)),
                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))),
                alpha = 0.6f
            )
        }
        
        Icon(
            if (isFirewallActive) Icons.Default.Shield else Icons.Default.ShieldMoon,
            contentDescription = null,
            tint = if (isFirewallActive) AccentCyan else DangerRed,
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                }
        )
    }
}

@Composable
fun QuickStatsRowRED(data: AnalyticsOut) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CentinelStatCard("Threats", data.high_risk_count.toString(), Icons.Default.BugReport, DangerRed, Modifier.weight(1f))
        CentinelStatCard("Safe", data.total_scans.toString(), Icons.Default.Verified, SuccessGreen, Modifier.weight(1f))
        CentinelStatCard("Score", data.average_risk_score.toInt().toString(), Icons.Default.Bolt, AccentCyan, Modifier.weight(1f))
    }
}
