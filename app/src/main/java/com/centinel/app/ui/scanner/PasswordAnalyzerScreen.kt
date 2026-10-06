package com.centinel.app.ui.scanner

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.centinel.app.data.model.PasswordScanResponse
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

class PasswordAnalyzerViewModel(private val repo: CentinelRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<PasswordScanResponse>>(UiState.Idle)
    val state: StateFlow<UiState<PasswordScanResponse>> = _state

    fun scan(pw: String) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.scanPassword(pw)) {
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
fun PasswordAnalyzerScreen(repo: CentinelRepository, onBack: () -> Unit) {
    val vm: PasswordAnalyzerViewModel = viewModel(factory = ViewModelFactory(repo))
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    val state by vm.state.collectAsState()

    CentinelScannerBase(
        title = "Password Strength",
        subtitle = "Evaluate password strength, entropy bits, and breach exposure 100% on-device.",
        icon = Icons.Default.Password,
        onBack = onBack
    ) {
        CentinelTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password String",
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            icon = Icons.Default.Password,
            trailingIcon = {
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                        tint = TextSecondary
                    )
                }
            }
        )
        
        Spacer(Modifier.height(32.dp))
        
        CentinelButton(
            text = "Analyze Password Strength",
            loading = state is UiState.Loading,
            enabled = password.isNotBlank(),
            onClick = { vm.scan(password.trim()) },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(40.dp))
        
        when (val s = state) {
            is UiState.Success<PasswordScanResponse> -> PasswordResultCardRED(s.data)
            is UiState.Error -> ErrorBanner(s.message)
            else -> {}
        }
    }
}

@Composable
fun PasswordResultCardRED(result: PasswordScanResponse) {
    val color = threatLevelColor(result.threat_level)
    val details = result.details

    Column {
        CentinelGlassCard(glowColor = color.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("CRACK RESISTANCE", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(details?.strength?.uppercase() ?: "UNKNOWN", style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Black)
                }
                Box(modifier = Modifier.size(60.dp).background(color.copy(alpha = 0.15f), CircleShape).border(1.dp, color.copy(alpha = 0.3f), CircleShape), contentAlignment = Alignment.Center) {
                    Text("${result.risk_score}", style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(result.explanation, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
        
        Spacer(Modifier.height(24.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatItemSmall("LENGTH", details?.length?.toString() ?: "0", Modifier.weight(1f))
            StatItemSmall("ENTROPY", "${details?.entropy_bits?.toInt() ?: 0} bits", Modifier.weight(1f))
        }

        if (result.recommendations.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("HARDENING PROTOCOLS", style = MaterialTheme.typography.labelSmall, color = AccentCyan, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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

@Composable
fun StatItemSmall(label: String, value: String, modifier: Modifier = Modifier) {
    CentinelGlassCard(modifier = modifier, cornerRadius = 16.dp) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        Text(value, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
    }
}
