package com.centinel.app.ui.scanner

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.centinel.app.data.model.HeaderScanResponse
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

class HeaderAnalyzerViewModel(private val repo: CentinelRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<HeaderScanResponse>>(UiState.Idle)
    val state: StateFlow<UiState<HeaderScanResponse>> = _state

    fun scan(headers: String) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.scanHeader(headers)) {
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
fun HeaderAnalyzerScreen(repo: CentinelRepository, onBack: () -> Unit) {
    val vm: HeaderAnalyzerViewModel = viewModel(factory = ViewModelFactory(repo))
    var headers by remember { mutableStateOf("") }
    val state by vm.state.collectAsState()

    CentinelScannerBase(
        title = "Header Analysis",
        subtitle = "Extract sender intelligence from raw mail routing data.",
        icon = Icons.Default.Dns,
        onBack = onBack
    ) {
        CentinelTextField(
            value = headers,
            onValueChange = { headers = it },
            label = "Raw SMTP Headers",
            modifier = Modifier.height(200.dp)
        )
        
        Spacer(Modifier.height(32.dp))
        
        CentinelButton(
            text = "Parse Routing Data",
            loading = state is UiState.Loading,
            enabled = headers.isNotBlank(),
            onClick = { vm.scan(headers.trim()) },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(40.dp))
        
        when (val s = state) {
            is UiState.Success<com.centinel.app.data.model.HeaderScanResponse> -> HeaderResultCardRED(s.data)
            is UiState.Error -> ErrorBanner(s.message)
            else -> {}
        }
    }
}

@Composable
fun HeaderResultCardRED(result: HeaderScanResponse) {
    val color = threatLevelColor(result.threat_level)
    val extracted = result.extracted?.jsonObject

    Column {
        CentinelGlassCard(glowColor = color.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("SENDER RELIABILITY", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(result.threat_level.uppercase(), style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Black)
                }
                Box(modifier = Modifier.size(60.dp).background(color.copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape).border(1.dp, color.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                    Text("${result.risk_score}", style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(result.explanation, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
        
        extracted?.let { 
            Spacer(Modifier.height(24.dp))
            CentinelGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                Text("ROUTING INTEL", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                CertDetailRow("Sender IP", it["sender_ip"]?.jsonPrimitive?.content ?: "Unknown")
                CertDetailRow("Country", it["country"]?.jsonPrimitive?.content ?: "Unknown")
                CertDetailRow("ISP", it["isp"]?.jsonPrimitive?.content ?: "Unknown")
                CertDetailRow("SPF/DKIM", it["spf_dkim_status"]?.jsonPrimitive?.content ?: "Unknown")
            }
        }
    }
}
