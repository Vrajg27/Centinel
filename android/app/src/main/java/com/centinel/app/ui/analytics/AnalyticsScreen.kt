package com.centinel.app.ui.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.centinel.app.data.model.AnalyticsOut
import com.centinel.app.data.repository.ApiResult
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.di.ViewModelFactory
import com.centinel.app.ui.common.BarDatum
import com.centinel.app.ui.common.ChartSlice
import com.centinel.app.ui.common.DonutChart
import com.centinel.app.ui.common.ErrorBanner
import com.centinel.app.ui.common.HorizontalBarChart
import com.centinel.app.ui.common.UiState
import com.centinel.app.ui.dashboard.StatChip
import com.centinel.app.ui.theme.threatLevelColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AnalyticsViewModel(private val repo: CentinelRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<AnalyticsOut>>(UiState.Idle)
    val state: StateFlow<UiState<AnalyticsOut>> = _state

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.getAnalytics()) {
                is ApiResult.Success -> _state.value = UiState.Success(res.data)
                is ApiResult.Error -> _state.value = UiState.Error(res.message)
            }
        }
    }
}

private val CATEGORY_PALETTE = listOf(
    Color(0xFF2563EB), Color(0xFF7C3AED), Color(0xFF0891B2), Color(0xFFCA8A04),
    Color(0xFFDB2777), Color(0xFF059669), Color(0xFFEA580C), Color(0xFF4F46E5),
    Color(0xFF64748B), Color(0xFF16A34A),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(repo: CentinelRepository, onBack: () -> Unit) {
    val vm: AnalyticsViewModel = viewModel(factory = ViewModelFactory(repo))
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.load() }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Threat Analytics") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
        })
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            when (val s = state) {
                is UiState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                is UiState.Error -> ErrorBanner(s.message)
                is UiState.Success -> {
                    val d = s.data
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatChip("Daily", d.daily_scans.toString())
                        StatChip("Weekly", d.weekly_scans.toString())
                        StatChip("Monthly", d.monthly_scans.toString())
                        StatChip("Total", d.total_scans.toString())
                    }
                    Spacer(Modifier.height(24.dp))

                    Text("Threat Level Distribution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    val levels = listOf("Safe", "Low", "Medium", "High", "Critical")
                    HorizontalBarChart(
                        data = levels.map { level ->
                            BarDatum(level, (d.threat_level_distribution[level] ?: 0).toFloat(), threatLevelColor(level))
                        },
                    )

                    Spacer(Modifier.height(28.dp))
                    Text("Scans by Category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    val categories = d.scans_by_type.entries.sortedByDescending { it.value }.toList()
                    DonutChart(
                        slices = categories.mapIndexed { i, entry ->
                            ChartSlice(entry.key.uppercase(), entry.value.toFloat(), CATEGORY_PALETTE[i % CATEGORY_PALETTE.size])
                        },
                    )
                }
                else -> {}
            }
        }
    }
}
