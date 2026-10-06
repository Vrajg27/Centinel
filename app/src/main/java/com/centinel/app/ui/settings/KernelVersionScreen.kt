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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.ui.components.*
import com.centinel.app.ui.theme.*

@Composable
fun KernelVersionScreen(
    repo: CentinelRepository,
    onBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val buildMetrics = listOf(
        "Kernel Version" to "2026.1.0-RELEASE",
        "Build Hash" to "0x8F9A2C041E92-RELEASE-ARM64",
        "Security Patch Level" to "August 2026",
        "Target Framework" to "Jetpack Compose M3 / SDK 35",
        "AI Engine Runtime" to "On-Device Neural Interop v4.2.0",
        "Encryption Standard" to "AES-256 Fernet Cipher"
    )

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
                    "Kernel Diagnostics & Metrics",
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
                // System Build Hero Card
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
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("SYSTEM KERNEL SPECIFICATION", style = MaterialTheme.typography.labelSmall, color = AccentCyan, fontWeight = FontWeight.Bold)
                            Text("Kernel Build Metrics", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text("Low-level framework metrics and build SHA hashes.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    "SYSTEM BUILD PARAMETERS",
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
                        buildMetrics.forEachIndexed { index, (label, value) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                            }
                            if (index < buildMetrics.size - 1) {
                                HorizontalDivider(color = GlassBorder)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                CentinelButton(
                    text = "Copy Kernel Diagnostics Log",
                    onClick = {
                        val logText = buildMetrics.joinToString("\n") { "${it.first}: ${it.second}" }
                        clipboardManager.setText(AnnotatedString(logText))
                        scope.launch {
                            snackbarHostState.showSnackbar("Kernel build metrics copied to clipboard.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.ContentCopy
                )

                Spacer(Modifier.height(48.dp))
            }
        }
    }
}
