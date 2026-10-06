package com.centinel.app.ui.scanner

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
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
import com.centinel.app.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlScannerScreen(repo: CentinelRepository, onBack: () -> Unit) {
    val vm: UrlScannerViewModel = viewModel(factory = ViewModelFactory(repo))
    var url by remember { mutableStateOf("") }
    val state by vm.state.collectAsState()

    CentinelScannerBase(
        title = "URL Scanner",
        subtitle = "Check URLs for phishing, malware, and malicious redirects.",
        icon = Icons.Default.Link,
        onBack = onBack
    ) {
        CentinelTextField(
            value = url,
            onValueChange = { url = it },
            label = "Target Intelligence URL",
            icon = Icons.Default.Link
        )
        
        Spacer(Modifier.height(32.dp))
        
        CentinelButton(
            text = "Initiate Deep Scan",
            loading = state is UiState.Loading,
            enabled = url.isNotBlank(),
            onClick = { vm.scan(url.trim()) },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(40.dp))
        
        when (val s = state) {
            is UiState.Success<com.centinel.app.data.model.ScanResult> -> ScanResultCard(s.data)
            is UiState.Error -> ErrorBanner(s.message)
            else -> {}
        }
    }
}
