package com.centinel.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

data class ChartSlice(val label: String, val value: Float, val color: Color)

/**
 * A simple donut chart drawn with Compose's Canvas API (no external charting
 * library — keeps this compiling reliably without a way to build/test the
 * app in this environment). Renders a colored ring proportional to each
 * slice's share of the total, with a legend below.
 */
@Composable
fun DonutChart(slices: List<ChartSlice>, modifier: Modifier = Modifier, strokeWidthDp: Float = 28f) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat()

    Column(modifier) {
        if (total <= 0f || slices.isEmpty()) {
            Text("No data yet", style = MaterialTheme.typography.bodySmall)
            return@Column
        }

        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            val strokePx = strokeWidthDp.dp.toPx()
            val diameter = size.minDimension - strokePx
            val topLeft = androidx.compose.ui.geometry.Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f,
            )
            var startAngle = -90f
            slices.forEach { slice ->
                val sweep = 360f * (slice.value / total)
                if (sweep > 0f) {
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = strokePx),
                    )
                }
                startAngle += sweep
            }
        }

        Spacer(Modifier.height(12.dp))
        slices.filter { it.value > 0f }.forEach { slice ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(slice.color, shape = androidx.compose.foundation.shape.CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${slice.label} — ${slice.value.toInt()} (${(100 * slice.value / total).toInt()}%)",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

data class BarDatum(val label: String, val value: Float, val color: Color)

/**
 * A horizontal bar chart, also drawn with plain Compose layout (Box widths)
 * rather than Canvas — simpler and just as reliable for this shape of data.
 */
@Composable
fun HorizontalBarChart(data: List<BarDatum>, modifier: Modifier = Modifier) {
    val maxValue = (data.maxOfOrNull { it.value } ?: 1f).coerceAtLeast(1f)

    Column(modifier) {
        data.forEach { datum ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                Text(datum.label, modifier = Modifier.width(72.dp), style = MaterialTheme.typography.bodySmall)
                Box(Modifier.weight(1f).height(16.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction = (datum.value / maxValue).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(datum.color, shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(datum.value.toInt().toString(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
