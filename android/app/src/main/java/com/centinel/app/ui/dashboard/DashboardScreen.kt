package com.centinel.app.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.di.ViewModelFactory
import com.centinel.app.ui.common.UiState
import com.centinel.app.ui.navigation.Screen

data class ToolCardData(val title: String, val icon: ImageVector, val route: String)

val SECURITY_TIPS = listOf(
    "Never enter your password after clicking a link in an email or SMS — type the site's address yourself instead.",
    "A padlock icon means the connection is encrypted, not that the site is trustworthy — phishing sites use HTTPS too.",
    "Legitimate banks and government agencies will never ask you to share an OTP over the phone or in a text message.",
    "Check the sender's actual email address, not just the display name — \"Amazon Support\" can hide any address.",
    "Use a unique password for every account. A password manager makes this painless.",
    "Enable two-factor authentication wherever it's offered — it stops most account takeovers even if your password leaks.",
    "Urgency (\"act now or your account will be suspended\") is one of the most common phishing tactics — slow down.",
    "Hover over a link before tapping it to preview where it actually leads.",
    "Keep your apps and OS updated — many attacks exploit vulnerabilities that were already patched months earlier.",
    "Double-check the domain spelling. \"paypa1.com\" and \"paypal.com\" are not the same site.",
    "Be cautious of unexpected attachments, even from people you know — their account may be compromised.",
    "A QR code can hide any URL. Scan it, but review the destination before opening it.",
)

@Composable
fun SecurityTipCard() {
    val tip = remember { SECURITY_TIPS.random() }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
            Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Security Tip", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(2.dp))
                Text(tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

val TOOLS = listOf(
    ToolCardData("URL Scanner", Icons.Filled.Link, Screen.UrlScanner.route),
    ToolCardData("Website Scanner", Icons.Filled.Language, Screen.WebsiteScanner.route),
    ToolCardData("Email Scanner", Icons.Filled.Email, Screen.EmailScanner.route),
    ToolCardData("Header Analyzer", Icons.Filled.Dns, Screen.HeaderAnalyzer.route),
    ToolCardData("SMS Scanner", Icons.Filled.Sms, Screen.SmsScanner.route),
    ToolCardData("QR Scanner", Icons.Filled.QrCodeScanner, Screen.QrScanner.route),
    ToolCardData("File Scanner", Icons.Filled.UploadFile, Screen.FileScanner.route),
    ToolCardData("Password Analyzer", Icons.Filled.Password, Screen.PasswordAnalyzer.route),
    ToolCardData("SSL Checker", Icons.Filled.Lock, Screen.SslChecker.route),
    ToolCardData("Data Breach Check", Icons.Filled.Warning, Screen.BreachChecker.route),
    ToolCardData("Scan History", Icons.Filled.History, Screen.History.route),
    ToolCardData("Threat Analytics", Icons.Filled.BarChart, Screen.Analytics.route),
    ToolCardData("Notifications", Icons.Filled.Notifications, Screen.Notifications.route),
)

@Composable
fun DashboardScreen(repo: CentinelRepository, onNavigate: (String) -> Unit, onLogout: () -> Unit) {
    val vm: DashboardViewModel = viewModel(factory = ViewModelFactory(repo))
    val analyticsState by vm.analyticsState.collectAsState()

    LaunchedEffect(Unit) { vm.loadAnalytics() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Centinel Dashboard") },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Filled.Logout, contentDescription = "Logout")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            when (val s = analyticsState) {
                is UiState.Success -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatChip("Total Scans", s.data.total_scans.toString())
                        StatChip("This Week", s.data.weekly_scans.toString())
                        StatChip("High Risk", s.data.high_risk_count.toString())
                        StatChip("Avg Risk", s.data.average_risk_score.toInt().toString())
                    }
                    Spacer(Modifier.height(16.dp))
                }
                is UiState.Loading -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                }
                else -> {}
            }

            SecurityTipCard()
            Spacer(Modifier.height(16.dp))

            Text("Security Tools", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            LazyVerticalGrid(columns = GridCells.Fixed(2), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(TOOLS) { tool ->
                    ToolCard(tool) { onNavigate(tool.route) }
                }
            }
        }
    }
}

@Composable
fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun ToolCard(tool: ToolCardData, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().height(100.dp)) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            Icon(tool.icon, contentDescription = tool.title, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(tool.title, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
