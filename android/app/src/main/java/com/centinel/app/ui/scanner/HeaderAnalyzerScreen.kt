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
import com.centinel.app.data.model.HeaderScanResponse
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
import kotlinx.serialization.json.*

class HeaderAnalyzerViewModel(private val repo: CentinelRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<HeaderScanResponse>>(UiState.Idle)
    val state: StateFlow<UiState<HeaderScanResponse>> = _state

    fun scan(rawHeaders: String) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.scanHeader(rawHeaders)) {
                is ApiResult.Success -> _state.value = UiState.Success(res.data)
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

    Scaffold(topBar = {
        TopAppBar(title = { Text("Email Header Analyzer") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
        })
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Paste raw email headers to trace the sender IP, relay chain, and authentication results.")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = headers, onValueChange = { headers = it },
                label = { Text("Raw headers") },
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Analyze Headers", loading = state is UiState.Loading, enabled = headers.isNotBlank()) {
                vm.scan(headers)
            }
            Spacer(Modifier.height(16.dp))
            when (val s = state) {
                is UiState.Success -> {
                    val r = s.data
                    val color = threatLevelColor(r.threat_level)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Risk Score: ${r.risk_score}/100  •  ${r.threat_level}", fontWeight = FontWeight.Bold, color = color)
                            Spacer(Modifier.height(8.dp))
                            Text(r.explanation)

                            r.extracted?.jsonObject?.get("sender_ip")?.let {
                                Spacer(Modifier.height(12.dp))
                                Text("Sender IP: ${it.jsonPrimitive.contentOrNull ?: "hidden"}", fontWeight = FontWeight.SemiBold)
                            }
                            r.extracted?.jsonObject?.get("relay_ips")?.jsonArray?.let { ips ->
                                if (ips.isNotEmpty()) {
                                    Text("Relay IPs: " + ips.joinToString { it.jsonPrimitive.content })
                                }
                            }
                            r.extracted?.jsonObject?.get("geo")?.takeIf { it != JsonNull }?.jsonObject?.let { geo ->
                                Spacer(Modifier.height(8.dp))
                                Text("Geolocation", fontWeight = FontWeight.SemiBold)
                                Text("Country: ${geo["country"]?.jsonPrimitive?.contentOrNull}")
                                Text("ISP: ${geo["isp"]?.jsonPrimitive?.contentOrNull}")
                                Text("ASN: ${geo["asn"]?.jsonPrimitive?.contentOrNull}")
                                geo["note"]?.jsonPrimitive?.contentOrNull?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
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
