package com.centinel.app.ui.notifications

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.centinel.app.data.model.NotificationOut
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(repo: CentinelRepository, onBack: () -> Unit) {
    val vm: NotificationsViewModel = viewModel(factory = ViewModelFactory(repo))
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.load() }

    CentinelScannerBase(
        title = "Comm Center",
        subtitle = "Priority alerts and secure neural link transmissions.",
        icon = Icons.Default.Notifications,
        onBack = onBack,
        scrollable = false // CRITICAL: Disable internal scroll to avoid crash with LazyColumn
    ) {
        Box(Modifier.fillMaxSize()) {
            when (val s = state) {
                is UiState.Loading -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = AccentCyan)
                }
                is UiState.Error -> ErrorBanner(s.message)
                is UiState.Success<List<NotificationOut>> -> {
                    if (s.data.isEmpty()) {
                        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(64.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("NO NEW DATA", style = MaterialTheme.typography.titleSmall, color = TextSecondary, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 120.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(s.data, key = { index, n -> "${n.id}_$index" }) { _, n ->
                                RedesignedNotificationCard(
                                    notification = n,
                                    onClick = { if (!n.is_read) vm.markRead(n.id) }
                                )
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
fun RedesignedNotificationCard(notification: NotificationOut, onClick: () -> Unit) {
    val severityColor = when (notification.severity.lowercase()) {
        "critical" -> DangerRed
        "high" -> WarningOrange
        else -> PrimaryBlue
    }

    CentinelGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        glowColor = if (!notification.is_read) severityColor.copy(alpha = 0.1f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (notification.is_read) Color.Transparent else severityColor)
                    .align(Alignment.Top)
                    .offset(y = 6.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            )
            
            Spacer(Modifier.width(16.dp))
            
            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        notification.title.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (notification.is_read) TextSecondary else TextPrimary,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    notification.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    notification.created_at,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

class NotificationsViewModel(private val repo: CentinelRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<NotificationOut>>>(UiState.Idle)
    val state: StateFlow<UiState<List<NotificationOut>>> = _state

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            try {
                when (val res = repo.getNotifications()) {
                    is ApiResult.Success -> _state.value = UiState.Success(res.data)
                    is ApiResult.Error -> _state.value = UiState.Error(res.message)
                }
            } catch (e: Exception) {
                _state.value = UiState.Error("Comm link disrupted.")
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
