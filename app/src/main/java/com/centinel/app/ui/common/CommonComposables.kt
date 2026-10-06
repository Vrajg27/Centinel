package com.centinel.app.ui.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.centinel.app.data.model.ScanResult
import com.centinel.app.ui.components.*
import com.centinel.app.ui.theme.*

sealed class UiState<out T> {
    object Idle : UiState<Nothing>()
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

/**
 * Premium 3D-styled Scan Result Card.
 */
@Composable
fun ScanResultCard(result: ScanResult, modifier: Modifier = Modifier) {
    val color = threatLevelColor(result.threat_level)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
        label = "pulse"
    )

    CentinelGlassCard(
        modifier = modifier.fillMaxWidth(),
        glowColor = color.copy(alpha = 0.1f)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        "THREAT LEVEL",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        result.threat_level.uppercase(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = color
                    )
                }

                val hasAi = remember(result.detected_threats) {
                    result.detected_threats.any { it.contains("ai_", ignoreCase = true) }
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (hasAi) {
                        Surface(
                            color = AccentCyan.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(10.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("AI-POWERED", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = AccentCyan, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(color.copy(alpha = pulseAlpha), CircleShape)
                            .border(1.dp, color.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${result.risk_score}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(SurfaceVariant, RoundedCornerShape(5.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(result.risk_score / 100f)
                        .fillMaxHeight()
                        .background(
                            brush = Brush.linearGradient(listOf(color, color.copy(alpha = 0.6f))),
                            shape = RoundedCornerShape(5.dp)
                        )
                        .shadow(4.dp, RoundedCornerShape(5.dp), spotColor = color)
                )
            }
            
            Spacer(Modifier.height(32.dp))
            
            CentinelGlassCard(
                cornerRadius = 18.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Analysis Summary",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    result.explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    lineHeight = 22.sp
                )
            }

            if (result.indicators.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Detected Indicators",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue
                )
                Spacer(Modifier.height(12.dp))
                result.indicators.forEach { indicator ->
                    IndicatorRow(indicator, color)
                }
            }
            
            if (result.recommendations.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                HorizontalDivider(color = GlassBorder)
                Spacer(Modifier.height(24.dp))
                Text(
                    "Recommended Actions",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = SecondaryPurple
                )
                Spacer(Modifier.height(16.dp))
                result.recommendations.forEach { rec ->
                    RecommendationRow(rec)
                }
            }
        }
    }
}

@Composable
fun IndicatorRow(text: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(SurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .border(1.dp, Brush.linearGradient(listOf(color.copy(alpha = 0.5f), GlassBorder)), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(color.copy(alpha = 0.18f), CircleShape)
                .border(1.dp, color.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Radar,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun RecommendationRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(CyberPurple.copy(alpha = 0.2f), CircleShape)
                .border(1.dp, CyberPurple.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                tint = CyberCyan,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    CentinelGlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        glowColor = DangerRed.copy(alpha = 0.2f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Error, contentDescription = null, tint = DangerRed)
            Spacer(Modifier.width(12.dp))
            Text(message, color = DangerRed, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun PrimaryButton(text: String, loading: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    CentinelButton(
        text = text,
        onClick = onClick,
        loading = loading,
        enabled = enabled
    )
}
