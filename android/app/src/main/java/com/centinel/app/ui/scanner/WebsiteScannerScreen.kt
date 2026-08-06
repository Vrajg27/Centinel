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
import com.centinel.app.data.model.WebsiteScanResponse
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

class WebsiteScannerViewModel(private val repo: CentinelRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<WebsiteScanResponse>>(UiState.Idle)
    val state: StateFlow<UiState<WebsiteScanResponse>> = _state

    fun scan(url: String) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.scanWebsite(url)) {
                is ApiResult.Success -> _state.value = UiState.Success(res.data)
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

    Scaffold(topBar = {
        TopAppBar(title = { Text("Website Scanner") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
        })
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text(
                "Unlike the URL Scanner, this actually loads the page: follows every " +
                    "redirect, and inspects the landing page for hidden iframes, obfuscated " +
                    "JavaScript, and login forms that send credentials somewhere suspicious.",
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("https://example.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Scan Website", loading = state is UiState.Loading, enabled = url.isNotBlank()) {
                vm.scan(url.trim())
            }
            Spacer(Modifier.height(16.dp))
            when (val s = state) {
                is UiState.Success -> WebsiteResultCard(s.data)
                is UiState.Error -> ErrorBanner(s.message)
                else -> {}
            }
        }
    }
}

@Composable
private fun WebsiteResultCard(result: WebsiteScanResponse) {
    val color = threatLevelColor(result.threat_level)
    val extracted = result.extracted?.jsonObject

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("${result.risk_score}/100 • ${result.threat_level}", fontWeight = FontWeight.Bold, color = color)
            Spacer(Modifier.height(8.dp))
            Text(result.explanation)

            extracted?.get("redirect_hop_count")?.jsonPrimitive?.intOrNull?.let { hops ->
                if (hops > 0) {
                    Spacer(Modifier.height(12.dp))
                    Text("Redirected $hops time(s) before landing", fontWeight = FontWeight.SemiBold)
                }
            }
            extracted?.get("redirect_chain")?.jsonArray?.let { chain ->
                if (chain.size > 1) {
                    chain.forEachIndexed { i, u ->
                        Text("${i + 1}. ${u.jsonPrimitive.content}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            val hasLoginForm = extracted?.get("login_form_detected")?.jsonPrimitive?.booleanOrNull ?: false
            val credExfil = extracted?.get("credential_exfil_risk")?.jsonPrimitive?.booleanOrNull ?: false
            if (hasLoginForm) {
                Spacer(Modifier.height(12.dp))
                Text(
                    if (credExfil) "⚠ Login form detected — submits to a different domain than the page (credential harvesting risk)"
                    else "Login form detected on this page",
                    color = if (credExfil) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (credExfil) FontWeight.Bold else FontWeight.Normal,
                )
            }

            extracted?.get("suspicious_js_patterns")?.jsonArray?.let { patterns ->
                if (patterns.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Suspicious JavaScript patterns", fontWeight = FontWeight.SemiBold)
                    patterns.forEach { Text("• ${it.jsonPrimitive.content}", style = MaterialTheme.typography.bodySmall) }
                }
            }

            extracted?.get("fetch_error")?.jsonPrimitive?.contentOrNull?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            if (result.recommendations.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Recommended Actions", fontWeight = FontWeight.SemiBold)
                result.recommendations.forEach { Text("• $it") }
            }
        }
    }
}
