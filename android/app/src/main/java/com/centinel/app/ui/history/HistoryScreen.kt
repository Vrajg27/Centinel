package com.centinel.app.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.centinel.app.data.model.ScanResult
import com.centinel.app.data.reports.ReportDownloadResult
import com.centinel.app.data.reports.ReportDownloader
import com.centinel.app.data.repository.ApiResult
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.di.ViewModelFactory
import com.centinel.app.ui.common.ErrorBanner
import com.centinel.app.ui.common.UiState
import com.centinel.app.ui.theme.threatLevelColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(private val repo: CentinelRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<ScanResult>>>(UiState.Idle)
    val state: StateFlow<UiState<List<ScanResult>>> = _state

    fun load(search: String? = null, scanType: String? = null) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.getHistory(scanType = scanType, search = search)) {
                is ApiResult.Success -> _state.value = UiState.Success(res.data)
                is ApiResult.Error -> _state.value = UiState.Error(res.message)
            }
        }
    }

    fun delete(id: String, currentSearch: String?, currentType: String?) {
        viewModelScope.launch {
            repo.deleteHistoryItem(id)
            load(currentSearch, currentType)
        }
    }
}

val SCAN_TYPES = listOf(null, "url", "website", "email", "header", "sms", "qr", "password", "ssl", "breach", "file")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(repo: CentinelRepository, onBack: () -> Unit) {
    val vm: HistoryViewModel = viewModel(factory = ViewModelFactory(repo))
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var search by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<String?>(null) }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) { vm.load() }

    fun downloadReport(scanId: String) {
        downloadingId = scanId
        scope.launch {
            when (val result = ReportDownloader.download(context, repo, scanId)) {
                is ReportDownloadResult.Success -> context.startActivity(result.openIntent)
                is ReportDownloadResult.Error -> snackbarHostState.showSnackbar(result.message)
            }
            downloadingId = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Scan History") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
            })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                label = { Text("Search history") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { TextButton(onClick = { vm.load(search.ifBlank { null }, selectedType) }) { Text("Go") } },
            )
            Spacer(Modifier.height(8.dp))
            LazyRow {
                items(SCAN_TYPES) { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type; vm.load(search.ifBlank { null }, type) },
                        label = { Text(type?.uppercase() ?: "ALL") },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            when (val s = state) {
                is UiState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                is UiState.Error -> ErrorBanner(s.message)
                is UiState.Success -> {
                    if (s.data.isEmpty()) {
                        Text("No scans yet.")
                    } else {
                        LazyColumn {
                            items(s.data, key = { it.id }) { item ->
                                HistoryRow(
                                    item = item,
                                    isDownloading = downloadingId == item.id,
                                    onDelete = { vm.delete(item.id, search.ifBlank { null }, selectedType) },
                                    onDownloadReport = { downloadReport(item.id) },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun LazyRow(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    androidx.compose.foundation.lazy.LazyRow { content() }
}

@Composable
fun HistoryRow(
    item: ScanResult,
    isDownloading: Boolean,
    onDelete: () -> Unit,
    onDownloadReport: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${item.scan_type.uppercase()}  •  ${item.target_summary}", fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(item.explanation, maxLines = 2, style = MaterialTheme.typography.bodySmall)
            Text(item.created_at, style = MaterialTheme.typography.labelSmall)
        }
        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
            Surface(color = threatLevelColor(item.threat_level), shape = androidx.compose.foundation.shape.RoundedCornerShape(50)) {
                Text(item.threat_level, color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
            }
            Row {
                IconButton(onClick = onDownloadReport, enabled = !isDownloading) {
                    if (isDownloading) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = "Download PDF report")
                    }
                }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
            }
        }
    }
}
