package com.centinel.app.ui.scanner

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Warning
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
import com.centinel.app.data.model.BreachScanResponse
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

class BreachScannerViewModel(private val repo: CentinelRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<BreachScanResponse>>(UiState.Idle)
    val state: StateFlow<UiState<BreachScanResponse>> = _state

    fun scan(email: String) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.scanBreach(email)) {
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
fun BreachCheckerScreen(repo: CentinelRepository, onBack: () -> Unit) {
    val vm: BreachScannerViewModel = viewModel(factory = ViewModelFactory(repo))
    var email by remember { mutableStateOf("") }
    val state by vm.state.collectAsState()

    CentinelScannerBase(
        title = "Breach Oracle",
        subtitle = "Scan the dark web for compromised identity credentials.",
        icon = Icons.Default.Warning,
        onBack = onBack
    ) {
        CentinelTextField(
            value = email,
            onValueChange = { email = it },
            label = "Target Identity Email",
            icon = Icons.Default.Email,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
        )
        
        Spacer(Modifier.height(32.dp))
        
        CentinelButton(
            text = "Consult Oracle",
            loading = state is UiState.Loading,
            enabled = email.isNotBlank(),
            onClick = { vm.scan(email.trim()) },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(40.dp))
        
        when (val s = state) {
            is UiState.Success<com.centinel.app.data.model.BreachScanResponse> -> BreachResultCardRED(s.data)
            is UiState.Error -> ErrorBanner(s.message)
            else -> {}
        }
    }
}

@Composable
fun BreachResultCardRED(result: BreachScanResponse) {
    val color = threatLevelColor(result.threat_level)
    val breaches = result.details?.jsonArray

    Column {
        CentinelGlassCard(glowColor = color.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("IDENTITY EXPOSURE", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("${breaches?.size ?: 0} BREACHES", style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Black)
                }
                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f)).padding(8.dp)) {
                    Text(result.threat_level.uppercase(), color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(result.explanation, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
        
        if (!breaches.isNullOrEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("EXPOSURE EVENTS", style = MaterialTheme.typography.labelSmall, color = AccentCyan, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(12.dp))
            breaches.forEach { b ->
                val obj = b.jsonObject
                CentinelGlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), cornerRadius = 18.dp) {
                    Column {
                        Text(obj["Name"]?.jsonPrimitive?.content ?: "Unknown Breach", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(obj["Domain"]?.jsonPrimitive?.content ?: "", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Leaked: " + (obj["DataClasses"]?.jsonArray?.joinToString(", ") { it.jsonPrimitive.content } ?: "Credentials"),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}
