package com.centinel.app.ui.history

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.centinel.app.ui.components.*
import com.centinel.app.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(private val repo: CentinelRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<ScanResult>>>(UiState.Idle)
    val state: StateFlow<UiState<List<ScanResult>>> = _state

    fun load(search: String? = null, scanType: String? = null) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            try {
                when (val res = repo.getHistory(scanType = scanType, search = search)) {
                    is ApiResult.Success -> _state.value = UiState.Success(res.data)
                    is ApiResult.Error -> _state.value = UiState.Error(res.message)
                }
            } catch (e: Exception) {
                _state.value = UiState.Error("Archive connection failure.")
            }
        }
    }

    fun delete(id: String, currentSearch: String?, currentType: String?) {
        viewModelScope.launch {
            repo.deleteHistoryItem(id)
            load(currentSearch, currentType)
        }
    }

    fun clearAllHistory(currentSearch: String?, currentType: String?) {
        viewModelScope.launch {
            if (_state.value is UiState.Success) {
                val list = (_state.value as UiState.Success<List<ScanResult>>).data
                list.forEach { repo.deleteHistoryItem(it.id) }
            }
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
    var showClearConfirmDialog by remember { mutableStateOf(false) }
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Background
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            CentinelScannerBase(
                title = "Security Scan Records",
                subtitle = "Encrypted repository of all processed security scan results.",
                icon = Icons.Default.History,
                onBack = onBack,
                scrollable = false // CRITICAL: Disable internal scroll to avoid crash with LazyColumn
            ) {
                if (showClearConfirmDialog) {
                    CentinelDialog(
                        onDismiss = { showClearConfirmDialog = false },
                        title = "Purge Scan Archive?",
                        confirmButton = {
                            TextButton(onClick = {
                                vm.clearAllHistory(search.ifBlank { null }, selectedType)
                                showClearConfirmDialog = false
                            }) {
                                Text("PURGE ALL", color = DangerRed, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearConfirmDialog = false }) {
                                Text("CANCEL", color = TextSecondary)
                            }
                        }
                    ) {
                        Text(
                            "This action will permanently remove all scan logs and security indicators from local storage. This action cannot be undone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("LOCAL ARCHIVE", style = MaterialTheme.typography.labelSmall, color = IceBlue, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showClearConfirmDialog = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = DangerRed, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Purge Archive", color = DangerRed, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Use weight(1f) to ensure the LazyColumn takes up the remaining space below the fixed header
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    item {
                        CentinelTextField(
                            value = search,
                            onValueChange = { 
                                search = it
                                vm.load(it.ifBlank { null }, selectedType)
                            },
                            label = "Query Data Matrix",
                            icon = Icons.Default.Search
                        )
                    }
                    
                    item {
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(SCAN_TYPES, key = { it ?: "all" }) { type ->
                                val isSelected = selectedType == type
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) PrimaryBlue else SurfaceVariant)
                                        .border(1.dp, if (isSelected) PrimaryBlue else GlassBorder, RoundedCornerShape(12.dp))
                                        .clickable { 
                                            selectedType = type
                                            vm.load(search.ifBlank { null }, type)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        type?.uppercase() ?: "ALL INTEL",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) Color.White else TextSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    
                    item {
                        Spacer(Modifier.height(8.dp))
                    }

                    when (val s = state) {
                        is UiState.Loading -> {
                            item {
                                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = AccentCyan)
                                }
                            }
                        }
                        is UiState.Error -> {
                            item {
                                ErrorBanner(s.message)
                            }
                        }
                        is UiState.Success<List<ScanResult>> -> {
                            if (s.data.isEmpty()) {
                                item {
                                    EmptyStateArchiveRED()
                                }
                            } else {
                                itemsIndexed(s.data, key = { index, item -> "${item.id}_$index" }) { _, item ->
                                    RedesignedHistoryCardRED(
                                        item = item,
                                        isDownloading = downloadingId == item.id,
                                        onDelete = { vm.delete(item.id, search.ifBlank { null }, selectedType) },
                                        onDownloadReport = { downloadReport(item.id) },
                                    )
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
fun RedesignedHistoryCardRED(
    item: ScanResult,
    isDownloading: Boolean,
    onDelete: () -> Unit,
    onDownloadReport: () -> Unit,
) {
    val threatColor = threatLevelColor(item.threat_level)
    
    CentinelGlassCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = threatColor.copy(alpha = 0.05f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(threatColor.copy(alpha = 0.1f))
                    .border(1.dp, threatColor.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (item.scan_type.lowercase()) {
                        "url" -> Icons.Default.Link
                        "email" -> Icons.Default.Email
                        "password" -> Icons.Default.Password
                        "qr" -> Icons.Default.QrCodeScanner
                        else -> Icons.Default.Security
                    },
                    contentDescription = null,
                    tint = threatColor,
                    modifier = Modifier.size(26.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(Modifier.weight(1f)) {
                Text(
                    item.target_summary,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${item.scan_type.uppercase()} • ${item.created_at}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    letterSpacing = 0.5.sp
                )
            }
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(threatColor.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    item.threat_level.uppercase(),
                    color = threatColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))
        Text(
            item.explanation, 
            maxLines = 2, 
            style = MaterialTheme.typography.bodySmall, 
            color = TextSecondary,
            lineHeight = 18.sp
        )
        
        Spacer(Modifier.height(20.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("DATA DECRYPTED", style = MaterialTheme.typography.labelSmall, color = SuccessGreen.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
            
            Row {
                IconButton(
                    onClick = onDownloadReport,
                    enabled = !isDownloading,
                    modifier = Modifier.background(SurfaceVariant, CircleShape).size(38.dp)
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = AccentCyan)
                    } else {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.background(DangerRed.copy(alpha = 0.1f), CircleShape).size(38.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = DangerRed, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun EmptyStateArchiveRED() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.HistoryToggleOff, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(80.dp))
        Spacer(Modifier.height(24.dp))
        Text("ARCHIVE EMPTY", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
        Text("No intelligence reports found in the repository.", style = MaterialTheme.typography.bodySmall, color = TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
