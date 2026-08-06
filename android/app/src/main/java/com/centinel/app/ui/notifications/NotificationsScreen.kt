package com.centinel.app.ui.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.centinel.app.data.model.NotificationOut
import com.centinel.app.data.repository.ApiResult
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.di.ViewModelFactory
import com.centinel.app.ui.common.ErrorBanner
import com.centinel.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NotificationsViewModel(private val repo: CentinelRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<NotificationOut>>>(UiState.Idle)
    val state: StateFlow<UiState<List<NotificationOut>>> = _state

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.getNotifications()) {
                is ApiResult.Success -> _state.value = UiState.Success(res.data)
                is ApiResult.Error -> _state.value = UiState.Error(res.message)
            }
        }
    }

    fun markRead(id: String) {
        viewModelScope.launch {
            repo.markNotificationRead(id)
            load()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(repo: CentinelRepository, onBack: () -> Unit) {
    val vm: NotificationsViewModel = viewModel(factory = ViewModelFactory(repo))
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.load() }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Notifications") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
        })
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            when (val s = state) {
                is UiState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                is UiState.Error -> ErrorBanner(s.message)
                is UiState.Success -> {
                    if (s.data.isEmpty()) {
                        Text("No notifications yet. High and Critical risk scans will appear here automatically.")
                    } else {
                        LazyColumn {
                            items(s.data, key = { it.id }) { n ->
                                Card(
                                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    onClick = { if (!n.is_read) vm.markRead(n.id) },
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text(n.title, fontWeight = FontWeight.SemiBold)
                                            if (!n.is_read) Text("NEW", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                        }
                                        Text(n.message, style = MaterialTheme.typography.bodySmall)
                                        Text(n.created_at, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}
