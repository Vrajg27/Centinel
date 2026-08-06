package com.centinel.app.ui.scanner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.di.ViewModelFactory
import com.centinel.app.ui.common.ErrorBanner
import com.centinel.app.ui.common.PrimaryButton
import com.centinel.app.ui.common.ScanResultCard
import com.centinel.app.ui.common.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlScannerScreen(repo: CentinelRepository, onBack: () -> Unit) {
    val vm: UrlScannerViewModel = viewModel(factory = ViewModelFactory(repo))
    var url by remember { mutableStateOf("") }
    val state by vm.state.collectAsState()

    Scaffold(topBar = {
        TopAppBar(title = { Text("URL Scanner") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(androidx.compose.material.icons.Icons.Filled.ArrowBack, null) }
        })
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Paste a URL to check for phishing, malicious redirects, spoofed domains, and unsafe SSL/TLS.")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("https://example.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Scan URL", loading = state is UiState.Loading, enabled = url.isNotBlank()) {
                vm.scan(url.trim())
            }
            Spacer(Modifier.height(16.dp))
            when (val s = state) {
                is UiState.Success -> ScanResultCard(s.data)
                is UiState.Error -> ErrorBanner(s.message)
                else -> {}
            }
        }
    }
}
