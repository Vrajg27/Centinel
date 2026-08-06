package com.centinel.app.ui.scanner

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.centinel.app.data.model.ScanResult
import com.centinel.app.data.repository.ApiResult
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.di.ViewModelFactory
import com.centinel.app.ui.common.ErrorBanner
import com.centinel.app.ui.common.PrimaryButton
import com.centinel.app.ui.common.ScanResultCard
import com.centinel.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class EmailScannerViewModel(private val repo: CentinelRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<ScanResult>>(UiState.Idle)
    val state: StateFlow<UiState<ScanResult>> = _state

    fun scan(subject: String?, sender: String?, body: String?) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.scanEmail(subject, sender, body, rawEmail = null)) {
                is ApiResult.Success -> _state.value = UiState.Success(res.data)
                is ApiResult.Error -> _state.value = UiState.Error(res.message)
            }
        }
    }

    fun scanFile(fileName: String, bytes: ByteArray) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.scanEmailFile(fileName, bytes)) {
                is ApiResult.Success -> _state.value = UiState.Success(res.data)
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

    Scaffold(topBar = {
        TopAppBar(title = { Text("Email Scanner") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
        })
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Paste the sender, subject, and body of a suspicious email — or upload a raw .eml file below.")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = sender, onValueChange = { sender = it }, label = { Text("From (e.g. \"Bank\" <alerts@bank-secure.tk>)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = body, onValueChange = { body = it }, label = { Text("Email body") },
                modifier = Modifier.fillMaxWidth().height(180.dp),
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Scan Email", loading = state is UiState.Loading, enabled = body.isNotBlank() || subject.isNotBlank()) {
                vm.scan(subject.ifBlank { null }, sender.ifBlank { null }, body.ifBlank { null })
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(Modifier.weight(1f))
                Text("  or  ", style = MaterialTheme.typography.labelSmall)
                HorizontalDivider(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { emlLauncher.launch("*/*") },
                enabled = state !is UiState.Loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.AttachFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(pickedFileName ?: "Upload .eml file")
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
