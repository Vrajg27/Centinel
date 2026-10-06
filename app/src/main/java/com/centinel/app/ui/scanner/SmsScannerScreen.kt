package com.centinel.app.ui.scanner

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.di.ViewModelFactory
import com.centinel.app.ui.common.ErrorBanner
import com.centinel.app.ui.common.ScanResultCard
import com.centinel.app.ui.common.UiState
import com.centinel.app.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsScannerScreen(repo: CentinelRepository, onBack: () -> Unit) {
    val vm: SmsScannerViewModel = viewModel(factory = ViewModelFactory(repo))
    var sender by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val state by vm.state.collectAsState()

    CentinelScannerBase(
        title = "SMS Scanner",
        subtitle = "Detect smishing, OTP fraud, and banking scams.",
        icon = Icons.Default.Sms,
        onBack = onBack
    ) {
        CentinelTextField(
            value = sender,
            onValueChange = { sender = it },
            label = "Sender Identification",
            icon = Icons.Default.Person
        )
        
        Spacer(Modifier.height(20.dp))
        
        CentinelTextField(
            value = message,
            onValueChange = { message = it },
            label = "Intercepted Message Text",
            modifier = Modifier.height(160.dp)
        )
        
        Spacer(Modifier.height(32.dp))
        
        CentinelButton(
            text = "Analyze Data Payload",
            loading = state is UiState.Loading,
            enabled = message.isNotBlank(),
            onClick = { vm.scan(message.trim(), sender.ifBlank { null }) },
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
