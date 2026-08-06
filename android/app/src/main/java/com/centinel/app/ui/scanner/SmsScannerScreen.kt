package com.centinel.app.ui.scanner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
fun SmsScannerScreen(repo: CentinelRepository, onBack: () -> Unit) {
    val vm: SmsScannerViewModel = viewModel(factory = ViewModelFactory(repo))
    var sender by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val state by vm.state.collectAsState()

    Scaffold(topBar = {
        TopAppBar(title = { Text("SMS Scanner") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
        })
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Paste a suspicious SMS to detect banking fraud, OTP theft, fake delivery, and other scams.")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = sender, onValueChange = { sender = it }, label = { Text("Sender (optional)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = message, onValueChange = { message = it },
                label = { Text("SMS message text") },
                modifier = Modifier.fillMaxWidth().height(140.dp),
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Scan Message", loading = state is UiState.Loading, enabled = message.isNotBlank()) {
                vm.scan(message.trim(), sender.ifBlank { null })
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
