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
import kotlinx.coroutines.launch
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.ui.components.*
import com.centinel.app.ui.theme.*

@Composable
fun PrivacyManifestoScreen(
    repo: CentinelRepository,
    onBack: () -> Unit
) {
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
                    "Privacy Manifesto Document",
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
                // Header Card
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
                            Icon(Icons.Default.Description, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("CENTINEL SOVEREIGNTY PROTOCOL", style = MaterialTheme.typography.labelSmall, color = AccentCyan, fontWeight = FontWeight.Bold)
                            Text("The Privacy Manifesto", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text("Our inviolable commitments to zero-knowledge user privacy.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Manifesto Articles
                ManifestoArticleCard(
                    articleNumber = "ARTICLE I",
                    title = "Absolute Zero-Knowledge Architecture",
                    body = "Your scanned URLs, SMS payloads, file attachments, and email headers are processed transiently in volatile memory for threat analysis. No target data is ever stored on persistent disk or sold to third parties."
                )

                Spacer(Modifier.height(16.dp))

                ManifestoArticleCard(
                    articleNumber = "ARTICLE II",
                    title = "End-to-End Cryptographic Isolation",
                    body = "Scan history stored on your device or synced to the backend is encrypted using Fernet symmetric encryption keys. Database compromise alone cannot reveal unencrypted scan contents."
                )

                Spacer(Modifier.height(16.dp))

                ManifestoArticleCard(
                    articleNumber = "ARTICLE III",
                    title = "Unconditional User Sovereignty",
                    body = "You retain sole ownership of your identity and data. You can clear temporary scan caches, export scan reports, or terminate your account permanently at any time with a single tap."
                )

                Spacer(Modifier.height(16.dp))

                ManifestoArticleCard(
                    articleNumber = "ARTICLE IV",
                    title = "Zero Commercial Tracking & Telemetry",
                    body = "Centinel contains zero advertising SDKs, third-party analytics trackers, or user profiling cookies. Debug telemetry is strictly opt-in and anonymized."
                )

                Spacer(Modifier.height(32.dp))

                CentinelButton(
                    text = "Acknowledge Privacy Protocol",
                    onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Privacy manifesto acknowledged.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.CheckCircle
                )

                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

@Composable
fun ManifestoArticleCard(articleNumber: String, title: String, body: String) {
    CentinelGlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp
    ) {
        Column {
            Text(articleNumber, style = MaterialTheme.typography.labelSmall, color = AccentCyan, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}
