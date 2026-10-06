package com.centinel.app.ui.scanner

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.centinel.app.data.model.SslScanResponse
import com.centinel.app.data.repository.ApiResult
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.di.ViewModelFactory
import com.centinel.app.ui.common.ErrorBanner
import com.centinel.app.ui.common.UiState
import com.centinel.app.ui.components.*
import com.centinel.app.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

class SslScannerViewModel(private val repo: CentinelRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<SslScanResponse>>(UiState.Idle)
    val state: StateFlow<UiState<SslScanResponse>> = _state

    fun scan(host: String) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.scanSsl(host)) {
                is ApiResult.Success -> {
                    _state.value = UiState.Success(res.data)
                    repo.getAnalytics() // Live update global stats
                }
                is ApiResult.Error -> _state.value = UiState.Error(res.message)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SslCheckerScreen(repo: CentinelRepository, onBack: () -> Unit) {
    val vm: SslScannerViewModel = viewModel(factory = ViewModelFactory(repo))
    var host by remember { mutableStateOf("") }
    val state by vm.state.collectAsState()

    CentinelScannerBase(
        title = "TLS Inspector",
        subtitle = "Cryptographic verification of remote server endpoints.",
        icon = Icons.Default.Lock,
        onBack = onBack
    ) {
        CentinelTextField(
            value = host,
            onValueChange = { host = it },
            label = "Target Hostname (e.g. google.com)",
            icon = Icons.Default.Language
        )
        
        Spacer(Modifier.height(32.dp))
        
        CentinelButton(
            text = "Initiate Handshake",
            loading = state is UiState.Loading,
            enabled = host.isNotBlank(),
            onClick = { vm.scan(host.trim()) },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(40.dp))
        
        when (val s = state) {
            is UiState.Success<com.centinel.app.data.model.SslScanResponse> -> SslResultCardRED(s.data)
            is UiState.Error -> ErrorBanner(s.message)
            else -> {}
        }
    }
}

@Composable
fun SslResultCardRED(result: SslScanResponse) {
    val color = threatLevelColor(result.threat_level)
    val cert = result.certificate?.jsonObject

    Column {
        CentinelGlassCard(glowColor = color.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("HANDSHAKE SECURITY", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(if (result.risk_score < 30) "SECURE" else "VULNERABLE", style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Black)
                }
                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f)).padding(8.dp)) {
                    Text(result.threat_level.uppercase(), color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(result.explanation, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
        
        cert?.let { 
            Spacer(Modifier.height(24.dp))
            CentinelGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                Text("CERTIFICATE INTEL", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                CertDetailRow("Issuer", it["issuer"]?.jsonPrimitive?.content ?: "Unknown")
                CertDetailRow("Protocol", it["version"]?.jsonPrimitive?.content ?: "Unknown")
                CertDetailRow("Cipher", it["cipher"]?.jsonPrimitive?.content ?: "Unknown")
                CertDetailRow("Expires", it["expires"]?.jsonPrimitive?.content ?: "Unknown")
            }
        }
    }
}

@Composable
fun CertDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
