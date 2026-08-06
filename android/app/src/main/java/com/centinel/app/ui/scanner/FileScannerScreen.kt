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
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.centinel.app.data.model.FileScanResponse
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

class FileScannerViewModel(private val repo: CentinelRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<FileScanResponse>>(UiState.Idle)
    val state: StateFlow<UiState<FileScanResponse>> = _state

    fun scan(name: String, bytes: ByteArray) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.scanFile(name, bytes)) {
                is ApiResult.Success -> _state.value = UiState.Success(res.data)
                is ApiResult.Error -> _state.value = UiState.Error(res.message)
            }
        }
    }
}

private fun readBytes(context: Context, uri: Uri): Pair<String, ByteArray>? {
    val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        cursor.moveToFirst()
        if (idx >= 0) cursor.getString(idx) else "file"
    } ?: "file"
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    return name to bytes
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileScannerScreen(repo: CentinelRepository, onBack: () -> Unit) {
    val vm: FileScannerViewModel = viewModel(factory = ViewModelFactory(repo))
    val context = LocalContext.current
    var pickedName by remember { mutableStateOf<String?>(null) }
    val state by vm.state.collectAsState()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val pair = readBytes(context, uri) ?: return@rememberLauncherForActivityResult
        pickedName = pair.first
        vm.scan(pair.first, pair.second)
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("File Scanner") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
        })
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Upload an APK, PDF, DOCX, ZIP, EXE, or image to check its hash and for suspicious structure.")
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { launcher.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.UploadFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(pickedName ?: "Choose a file")
            }
            Spacer(Modifier.height(16.dp))
            when (val s = state) {
                is UiState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                is UiState.Success -> {
                    val color = threatLevelColor(s.data.threat_level)
                    val info = s.data.file_info?.jsonObject
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("${s.data.risk_score}/100 • ${s.data.threat_level}", fontWeight = FontWeight.Bold, color = color)
                            Spacer(Modifier.height(8.dp))
                            Text(s.data.explanation)
                            info?.get("sha256")?.jsonPrimitive?.contentOrNull?.let {
                                Spacer(Modifier.height(12.dp))
                                Text("SHA-256: $it", style = MaterialTheme.typography.bodySmall)
                            }
                            info?.get("size_bytes")?.jsonPrimitive?.contentOrNull?.let { Text("Size: $it bytes", style = MaterialTheme.typography.bodySmall) }
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
