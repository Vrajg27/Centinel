package com.centinel.app.ui.scanner

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.centinel.app.ui.common.UiState
import com.centinel.app.ui.components.*
import com.centinel.app.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FileScannerViewModel(private val repo: CentinelRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<FileScanResponse>>(UiState.Idle)
    val state: StateFlow<UiState<FileScanResponse>> = _state

    fun scan(fileName: String, bytes: ByteArray, mimeType: String) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.scanFile(fileName, bytes, mimeType)) {
                is ApiResult.Success -> {
                    _state.value = UiState.Success(res.data)
                    repo.getAnalytics() // Live update global stats
                }
                is ApiResult.Error -> _state.value = UiState.Error(res.message)
            }
        }
    }
}

private fun getFileInfo(context: Context, uri: Uri): Triple<String, ByteArray, String>? {
    val cr = context.contentResolver
    val name = cr.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        cursor.moveToFirst()
        if (idx >= 0) cursor.getString(idx) else "unknown_file"
    } ?: "unknown_file"
    val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val mime = cr.getType(uri) ?: "application/octet-stream"
    return Triple(name, bytes, mime)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileScannerScreen(repo: CentinelRepository, onBack: () -> Unit) {
    val vm: FileScannerViewModel = viewModel(factory = ViewModelFactory(repo))
    val context = LocalContext.current
    var pickedFile by remember { mutableStateOf<Triple<String, ByteArray, String>?>(null) }
    val state by vm.state.collectAsState()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        pickedFile = getFileInfo(context, uri)
    }

    CentinelScannerBase(
        title = "File Security Scanner",
        subtitle = "Static malware heuristics, magic byte headers, double extension checks, and SHA-256 hashing.",
        icon = Icons.Default.UploadFile,
        onBack = onBack
    ) {
        CentinelGlassCard(
            modifier = Modifier.fillMaxWidth(),
            glowColor = PrimaryGlow.copy(alpha = 0.1f)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Icon(Icons.Default.UploadFile, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    pickedFile?.first ?: "SELECT FILE TO ANALYZE",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (pickedFile != null) TextPrimary else TextSecondary,
                    fontWeight = FontWeight.Bold
                )
                if (pickedFile != null) {
                    Text("${pickedFile!!.second.size / 1024} KB • ${pickedFile!!.third}", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                }
                Spacer(Modifier.height(24.dp))
                CentinelButton(
                    text = if (pickedFile == null) "Choose Local File" else "Change Selected File",
                    onClick = { launcher.launch("*/*") },
                    colors = CyanGradient,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        CentinelButton(
            text = "Run File Security Scan",
            loading = state is UiState.Loading,
            enabled = pickedFile != null,
            onClick = { pickedFile?.let { vm.scan(it.first, it.second, it.third) } },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(40.dp))
        
        when (val s = state) {
            is UiState.Success<FileScanResponse> -> FileResultCardRED(s.data)
            is UiState.Error -> ErrorBanner(s.message)
            else -> {}
        }
    }
}

@Composable
fun FileResultCardRED(result: FileScanResponse) {
    val color = threatLevelColor(result.threat_level)
    Column {
        CentinelGlassCard(glowColor = color.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("STATIC ANALYSIS RISK", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("${result.risk_score}/100", style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Black)
                }
                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f)).padding(8.dp)) {
                    Text(result.threat_level.uppercase(), color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(result.explanation, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
        
        Spacer(Modifier.height(24.dp))
        Text("DETECTION INDICATORS", style = MaterialTheme.typography.labelSmall, color = AccentCyan, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        result.recommendations.forEach { rec ->
            CentinelGlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), cornerRadius = 14.dp) {
                Text(rec, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}
