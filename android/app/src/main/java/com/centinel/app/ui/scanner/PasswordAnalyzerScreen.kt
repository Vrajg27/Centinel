package com.centinel.app.ui.scanner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.centinel.app.data.model.PasswordScanResponse
import com.centinel.app.data.repository.ApiResult
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.di.ViewModelFactory
import com.centinel.app.ui.common.ErrorBanner
import com.centinel.app.ui.common.PrimaryButton
import com.centinel.app.ui.common.UiState
import com.centinel.app.ui.theme.threatLevelColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PasswordAnalyzerViewModel(private val repo: CentinelRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<PasswordScanResponse>>(UiState.Idle)
    val state: StateFlow<UiState<PasswordScanResponse>> = _state

    fun scan(password: String) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.scanPassword(password)) {
                is ApiResult.Success -> _state.value = UiState.Success(res.data)
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
    var visible by remember { mutableStateOf(false) }
    val state by vm.state.collectAsState()

    Scaffold(topBar = {
        TopAppBar(title = { Text("Password Analyzer") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
        })
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Your password is analyzed locally and is never stored in plaintext.")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { visible = !visible }) { Text(if (visible) "Hide" else "Show") }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Analyze Strength", loading = state is UiState.Loading, enabled = password.isNotBlank()) {
                vm.scan(password)
            }
            Spacer(Modifier.height(16.dp))
            when (val s = state) {
                is UiState.Success -> {
                    val d = s.data.details
                    val color = threatLevelColor(s.data.threat_level)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(d?.strength ?: s.data.threat_level, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(progress = { (d?.score ?: 0) / 100f }, modifier = Modifier.fillMaxWidth(), color = color)
                            Spacer(Modifier.height(12.dp))
                            d?.let {
                                Text("Length: ${it.length} characters")
                                Text("Estimated entropy: ${it.entropy_bits} bits")
                                if (it.is_common_password) Text("⚠ This is a commonly used/leaked password", color = MaterialTheme.colorScheme.error)
                                if (it.has_sequential_pattern) Text("⚠ Contains a sequential pattern", color = MaterialTheme.colorScheme.error)
                                if (it.has_repeated_chars) Text("⚠ Contains repeated characters", color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(12.dp))
                            }
                            Text(s.data.explanation)
                            Spacer(Modifier.height(12.dp))
                            Text("Recommendations", fontWeight = FontWeight.SemiBold)
                            s.data.recommendations.forEach { Text("• $it") }
                        }
                    }
                }
                is UiState.Error -> ErrorBanner(s.message)
                else -> {}
            }
        }
    }
}
