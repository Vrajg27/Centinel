package com.centinel.app.ui.scanner

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
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
import com.centinel.app.data.model.WebsiteScanResponse
import com.centinel.app.data.repository.ApiResult
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.di.ViewModelFactory
import com.centinel.app.ui.common.ErrorBanner
import com.centinel.app.ui.common.PrimaryButton
import com.centinel.app.ui.common.UiState
import com.centinel.app.ui.components.*
import com.centinel.app.ui.theme.*
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

class WebsiteScannerViewModel(private val repo: CentinelRepository) : androidx.lifecycle.ViewModel() {
    private val _state = MutableStateFlow<UiState<com.centinel.app.data.model.WebsiteScanResponse>>(UiState.Idle)
    val state: StateFlow<UiState<com.centinel.app.data.model.WebsiteScanResponse>> = _state

    fun scan(url: String) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.scanWebsite(url)) {
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
fun WebsiteScannerScreen(repo: CentinelRepository, onBack: () -> Unit) {
    val vm: WebsiteScannerViewModel = viewModel(factory = ViewModelFactory(repo))
    var url by remember { mutableStateOf("") }
    val state by vm.state.collectAsState()

    CentinelScannerBase(
        title = "Web Intelligence",
        subtitle = "Deep behavioral analysis of remote landing pages.",
        icon = Icons.Default.Language,
        onBack = onBack
    ) {
        CentinelTextField(
            value = url,
            onValueChange = { url = it },
            label = "Target Domain or URL",
            icon = Icons.Default.Language
        )
        
        Spacer(Modifier.height(32.dp))
        
        CentinelButton(
            text = "Initiate Protocol",
            loading = state is UiState.Loading,
            enabled = url.isNotBlank(),
            onClick = { vm.scan(url.trim()) },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(40.dp))
        
        when (val s = state) {
            is UiState.Success<com.centinel.app.data.model.WebsiteScanResponse> -> RedesignedWebsiteResultCard(s.data)
            is UiState.Error -> ErrorBanner(s.message)
            else -> {}
        }
    }
}

@Composable
private fun RedesignedWebsiteResultCard(result: WebsiteScanResponse) {
    val color = threatLevelColor(result.threat_level)
    val extracted = result.extracted?.jsonObject

    Column {
        CentinelGlassCard(
            modifier = Modifier.fillMaxWidth(),
            glowColor = color.copy(alpha = 0.1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("BEHAVIORAL RISK", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("${result.risk_score}/100", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = color)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(color.copy(alpha = 0.15f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(result.threat_level.uppercase(), color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            Text(result.explanation, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }

        extracted?.get("redirect_hop_count")?.jsonPrimitive?.intOrNull?.let { hops ->
            if (hops > 0) {
                Spacer(Modifier.height(24.dp))
                CentinelGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                    Text("Redirect Chain ($hops hops)", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    extracted["redirect_chain"]?.jsonArray?.forEachIndexed { i, u ->
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Text("${i + 1}.", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text(u.jsonPrimitive.content, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }
        }

        val hasLoginForm = extracted?.get("login_form_detected")?.jsonPrimitive?.booleanOrNull ?: false
        val credExfil = extracted?.get("credential_exfil_risk")?.jsonPrimitive?.booleanOrNull ?: false
        if (hasLoginForm) {
            Spacer(Modifier.height(24.dp))
            CentinelGlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 18.dp,
                glowColor = if (credExfil) DangerRed.copy(alpha = 0.2f) else SuccessGreen.copy(alpha = 0.1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).background(if (credExfil) DangerRed else SuccessGreen, CircleShape))
                    Spacer(Modifier.width(16.dp))
                    Text(
                        if (credExfil) "PHISHING RISK: Unauthorized Credential Harvesting Detected"
                        else "Secure Login Infrastructure Detected",
                        color = if (credExfil) DangerRed else TextPrimary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (credExfil) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        if (result.recommendations.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("Countermeasures", style = MaterialTheme.typography.labelSmall, color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            result.recommendations.forEach { rec ->
                CentinelGlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), cornerRadius = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(rec, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
        }
    }
}
