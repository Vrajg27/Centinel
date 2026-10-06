package com.centinel.app.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.centinel.app.data.model.AnalyticsOut
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.di.ViewModelFactory
import com.centinel.app.ui.common.ErrorBanner
import com.centinel.app.ui.components.*
import com.centinel.app.ui.theme.*
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AnalyticsViewModel(private val repo: CentinelRepository) : ViewModel() {
    val state: StateFlow<AnalyticsOut?> = repo.analyticsCache

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    fun load() {
        if (_isRefreshing.value) return
        
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repo.getAnalytics()
            } catch (e: Exception) {
                // Background refresh failed
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}

private val CATEGORY_PALETTE = listOf(
    PrimaryBlue, SecondaryPurple, AccentCyan, SuccessGreen,
    Color(0xFFF472B6), Color(0xFFFBBF24), Color(0xFF818CF8), Color(0xFF2DD4BF),
    Color(0xFFA1A1AA), Color(0xFFFB7185)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(repo: CentinelRepository, onBack: () -> Unit) {
    val vm: AnalyticsViewModel = viewModel(factory = ViewModelFactory(repo))
    val analytics by vm.state.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    
    LaunchedEffect(Unit) { 
        vm.load() 
    }

    CentinelScannerBase(
        title = "Threat Analytics",
        subtitle = "Deep insights into your digital security landscape.",
        icon = Icons.Default.Analytics,
        onBack = onBack
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (analytics == null && isRefreshing) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentCyan, strokeWidth = 3.dp)
                }
            } else if (analytics == null) {
                ErrorBanner(
                    message = "Intelligence archive offline. Re-establishing link...",
                    modifier = Modifier.padding(vertical = 24.dp)
                )
                CentinelButton(
                    text = "Initiate Protocol",
                    onClick = { vm.load() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CyanGradient
                )
            } else {
                val d = analytics!!

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard("TOTAL", d.total_scans.toString(), Modifier.weight(1f))
                    StatCard("WEEKLY", d.weekly_scans.toString(), Modifier.weight(1f))
                    StatCard("HIGH RISK", d.high_risk_count.toString(), Modifier.weight(1f))
                }

                if (isRefreshing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(2.dp),
                        color = AccentCyan,
                        trackColor = Color.Transparent
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Threat Level Distribution Card
                CentinelGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "THREAT DISTRIBUTION",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentCyan,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Box(
                            modifier = Modifier
                                .background(AccentCyan.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .border(1.dp, AccentCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "${d.total_scans} Scans",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    val levels = remember { listOf("Safe", "Low", "Medium", "High", "Critical") }
                    val values = remember(d.threat_level_distribution) {
                        levels.map { (d.threat_level_distribution[it] ?: 0).toFloat() }
                    }

                    ThreatDistributionChart(
                        labels = levels,
                        values = values,
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Scans By Category Card
                CentinelGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "SCANS BY CATEGORY",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentCyan,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Box(
                            modifier = Modifier
                                .background(PrimaryBlue.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .border(1.dp, PrimaryBlue.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "${d.scans_by_type.size} Categories",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = PrimaryBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    val categories = remember(d.scans_by_type) {
                        d.scans_by_type.entries.sortedByDescending { it.value }.toList()
                    }

                    ScansByCategoryChart(
                        labels = categories.map { formatCategoryCode(it.key) },
                        fullNames = categories.map { formatCategoryFullName(it.key) },
                        values = categories.map { it.value.toFloat() },
                    )
                }

                Spacer(Modifier.height(100.dp))
            }
        }
    }
}

private fun formatCategoryCode(key: String): String {
    return when (key.lowercase()) {
        "url" -> "URL"
        "email" -> "EML"
        "header" -> "HDR"
        "sms" -> "SMS"
        "qr" -> "QR"
        "file" -> "FILE"
        "password" -> "PASS"
        "ssl" -> "SSL"
        "breach" -> "BCH"
        "website" -> "WEB"
        else -> key.take(4).uppercase()
    }
}

private fun formatCategoryFullName(key: String): String {
    return when (key.lowercase()) {
        "url" -> "URL Scanner"
        "email" -> "Email Intel"
        "header" -> "Header Analyzer"
        "sms" -> "SMS Scanner"
        "qr" -> "QR Scanner"
        "file" -> "File Malware"
        "password" -> "Password Analysis"
        "ssl" -> "SSL Inspector"
        "breach" -> "Breach Oracle"
        "website" -> "Website Inspector"
        else -> key.replaceFirstChar { it.uppercase() }
    }
}

@Composable
private fun ThreatDistributionChart(labels: List<String>, values: List<Float>) {
    val total = remember(values) { values.sum() }
    if (values.isEmpty() || total <= 0f) {
        EmptyChartState()
        return
    }

    val colors = remember(labels) { labels.map { threatLevelColor(it) } }
    val modelProducer = remember { CartesianChartModelProducer() }
    
    LaunchedEffect(values) {
        try {
            modelProducer.runTransaction {
                columnSeries {
                    values.indices.forEach { i ->
                        series(*Array<Number>(values.size) { j -> if (j == i) values[i] else 0f })
                    }
                }
            }
        } catch (_: Exception) {}
    }

    Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                        colors.map { color ->
                            rememberLineComponent(
                                fill = fill(color),
                                thickness = 22.dp,
                                shape = CorneredShape.rounded(topLeftPercent = 35, topRightPercent = 30)
                            )
                        }
                    ),
                    mergeMode = { ColumnCartesianLayer.MergeMode.Stacked },
                ),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = { _, value, _ -> labels.getOrElse(value.toInt()) { "" } },
                ),
            ),
            modelProducer = modelProducer,
            modifier = Modifier.fillMaxSize(),
        )
    }

    Spacer(Modifier.height(20.dp))
    HorizontalDivider(color = GlassBorder)
    Spacer(Modifier.height(16.dp))

    ChartLegend(labels = labels, values = values, colors = colors, total = total)
}

@Composable
private fun ScansByCategoryChart(labels: List<String>, fullNames: List<String>, values: List<Float>) {
    val total = remember(values) { values.sum() }
    if (labels.isEmpty() || values.isEmpty() || total <= 0f) {
        EmptyChartState()
        return
    }

    val colors = remember(labels) { labels.mapIndexed { i, _ -> CATEGORY_PALETTE[i % CATEGORY_PALETTE.size] } }
    val modelProducer = remember { CartesianChartModelProducer() }
    
    LaunchedEffect(values) {
        try {
            modelProducer.runTransaction {
                columnSeries {
                    values.indices.forEach { i ->
                        series(*Array<Number>(values.size) { j -> if (j == i) values[i] else 0f })
                    }
                }
            }
        } catch (_: Exception) {}
    }

    Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                        colors.map { color ->
                            rememberLineComponent(
                                fill = fill(color),
                                thickness = 22.dp,
                                shape = CorneredShape.rounded(topLeftPercent = 35, topRightPercent = 30)
                            )
                        }
                    ),
                    mergeMode = { ColumnCartesianLayer.MergeMode.Stacked },
                ),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = { _, value, _ -> labels.getOrElse(value.toInt()) { "" } },
                ),
            ),
            modelProducer = modelProducer,
            modifier = Modifier.fillMaxSize(),
        )
    }

    Spacer(Modifier.height(20.dp))
    HorizontalDivider(color = GlassBorder)
    Spacer(Modifier.height(16.dp))

    ChartLegend(labels = fullNames, values = values, colors = colors, total = total)
}

@Composable
private fun EmptyChartState() {
    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        Text("No scan intelligence recorded yet", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun ChartLegend(labels: List<String>, values: List<Float>, colors: List<Color>, total: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, label ->
            val value = values[i]
            if (value > 0f) {
                val pct = if (total > 0f) (100 * value / total).toInt() else 0
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(colors[i], shape = CircleShape)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        "${value.toInt()} ($pct%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}
