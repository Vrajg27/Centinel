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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.centinel.app.data.model.SslScanResponse
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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SslCheckerViewModel(private val repo: CentinelRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<SslScanResponse>>(UiState.Idle)
    val state: StateFlow<UiState<SslScanResponse>> = _state

    fun scan(hostname: String) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.scanSsl(hostname)) {
                is ApiResult.Success -> _state.value = UiState.Success(res.data)
                is ApiResult.Error -> _state.value = UiState.Error(res.message)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SslCheckerScreen(repo: CentinelRepository, onBack: () -> Unit) {
    val vm: SslCheckerViewModel = viewModel(factory = ViewModelFactory(repo))
    var host by remember { mutableStateOf("") }
    val state by vm.state.collectAsState()

    Scaffold(topBar = {
        TopAppBar(title = { Text("SSL Certificate Checker") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
        })
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Enter a hostname to inspect its live TLS certificate: issuer, expiry, and validity.")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("example.com") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Check Certificate", loading = state is UiState.Loading, enabled = host.isNotBlank()) {
                vm.scan(host.trim())
            }
            Spacer(Modifier.height(16.dp))
            when (val s = state) {
                is UiState.Success -> {
                    val color = threatLevelColor(s.data.threat_level)
                    val cert = s.data.certificate?.jsonObject
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("${s.data.risk_score}/100 • ${s.data.threat_level}", fontWeight = FontWeight.Bold, color = color)
                            Spacer(Modifier.height(8.dp))
                            Text(s.data.explanation)
                            cert?.get("issuer")?.jsonPrimitive?.contentOrNull?.let {
                                Spacer(Modifier.height(12.dp))
                                Text("Issuer: $it")
                            }
                            cert?.get("valid_until")?.jsonPrimitive?.contentOrNull?.let { Text("Valid until: $it") }
                            cert?.get("days_until_expiry")?.jsonPrimitive?.contentOrNull?.let { Text("Days until expiry: $it") }
                            cert?.get("error")?.jsonPrimitive?.contentOrNull?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                is UiState.Error -> ErrorBanner(s.message)
                else -> {}
            }
        }
    }
}
