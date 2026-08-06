package com.centinel.app.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.centinel.app.data.model.ScanResult
import com.centinel.app.ui.theme.threatLevelColor

sealed class UiState<out T> {
    object Idle : UiState<Nothing>()
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

@Composable
fun ScanResultCard(result: ScanResult, modifier: Modifier = Modifier) {
    val color = threatLevelColor(result.threat_level)
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Risk Score: ${result.risk_score}/100", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(color = color, shape = RoundedCornerShape(50)) {
                    Text(
                        result.threat_level,
                        color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { result.risk_score / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = color,
            )
            Spacer(Modifier.height(12.dp))
            Text("Confidence: ${(result.confidence * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Text("Explanation", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(result.explanation, style = MaterialTheme.typography.bodyMedium)

            if (result.indicators.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Indicators", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                result.indicators.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
            if (result.recommendations.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Recommended Actions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                result.recommendations.forEach { Text("✓ $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp), modifier = modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
fun PrimaryButton(text: String, loading: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled && !loading, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
        } else {
            Text(text)
        }
    }
}
