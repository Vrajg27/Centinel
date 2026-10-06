package com.centinel.app.ui.scanner

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.centinel.app.data.repository.ApiResult
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.di.ViewModelFactory
import com.centinel.app.ui.common.ErrorBanner
import com.centinel.app.ui.common.ScanResultCard
import com.centinel.app.ui.common.UiState
import com.centinel.app.ui.components.*
import com.centinel.app.ui.theme.*

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class EmailScannerViewModel(private val repo: CentinelRepository) : androidx.lifecycle.ViewModel() {
    private val _state = MutableStateFlow<UiState<com.centinel.app.data.model.ScanResult>>(UiState.Idle)
    val state: StateFlow<UiState<com.centinel.app.data.model.ScanResult>> = _state

    fun scan(subject: String?, sender: String?, body: String?) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.scanEmail(subject, sender, body, rawEmail = null)) {
                is ApiResult.Success -> {
                    _state.value = UiState.Success(res.data)
                    repo.getAnalytics() // Live update global stats
                }
                is ApiResult.Error -> _state.value = UiState.Error(res.message)
            }
        }
    }

    fun scanFile(fileName: String, bytes: ByteArray) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.scanEmailFile(fileName, bytes)) {
                is ApiResult.Success -> {
                    _state.value = UiState.Success(res.data)
                    repo.getAnalytics() // Live update global stats
                }
                is ApiResult.Error -> _state.value = UiState.Error(res.message)
            }
        }
    }
}

private fun readUriBytes(context: Context, uri: Uri): Pair<String, ByteArray>? {
    val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        cursor.moveToFirst()
        if (idx >= 0) cursor.getString(idx) else "email.eml"
    } ?: "email.eml"
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    return name to bytes
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailScannerScreen(repo: CentinelRepository, onBack: () -> Unit) {
    val vm: EmailScannerViewModel = viewModel(factory = ViewModelFactory(repo))
    val context = LocalContext.current
    var sender by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var pickedFileName by remember { mutableStateOf<String?>(null) }
    val state by vm.state.collectAsState()

    val emlLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val pair = readUriBytes(context, uri) ?: return@rememberLauncherForActivityResult
        pickedFileName = pair.first
        vm.scanFile(pair.first, pair.second)
    }

    CentinelScannerBase(
        title = "Email Content Scanner",
        subtitle = "Identify phishing lures, display-name spoofing, and malicious links.",
        icon = Icons.Default.Email,
        onBack = onBack
    ) {
        CentinelTextField(
            value = sender,
            onValueChange = { sender = it },
            label = "Sender Email Address",
            icon = Icons.Default.Email
        )
        
        Spacer(Modifier.height(16.dp))
        
        CentinelTextField(
            value = subject,
            onValueChange = { subject = it },
            label = "Email Subject",
            icon = Icons.Default.Email
        )
        
        Spacer(Modifier.height(16.dp))
        
        CentinelTextField(
            value = body,
            onValueChange = { body = it },
            label = "Email Body Content",
            modifier = Modifier.height(180.dp)
        )
        
        Spacer(Modifier.height(24.dp))
        
        CentinelButton(
            text = "Analyze Email Security",
            loading = state is UiState.Loading,
            enabled = body.isNotBlank() || subject.isNotBlank(),
            onClick = { vm.scan(subject.ifBlank { null }, sender.ifBlank { null }, body.ifBlank { null }) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))
        
        CentinelGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ATTACHMENT FILE ANALYSIS", style = MaterialTheme.typography.labelSmall, color = AccentCyan)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { emlLauncher.launch("*/*") },
                    enabled = state !is UiState.Loading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue)
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(pickedFileName ?: "Upload Attachment File (.eml)", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        
        when (val s = state) {
            is UiState.Success<com.centinel.app.data.model.ScanResult> -> ScanResultCard(s.data)
            is UiState.Error -> ErrorBanner(s.message)
            else -> {}
        }
    }
}
