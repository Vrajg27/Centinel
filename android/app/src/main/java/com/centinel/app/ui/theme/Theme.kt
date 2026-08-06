package com.centinel.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CentinelBlue = Color(0xFF2563EB)
val CentinelDark = Color(0xFF0F172A)
val CentinelSafe = Color(0xFF16A34A)
val CentinelLow = Color(0xFF65A30D)
val CentinelMedium = Color(0xFFD97706)
val CentinelHigh = Color(0xFFEA580C)
val CentinelCritical = Color(0xFFDC2626)

fun threatLevelColor(level: String): Color = when (level) {
    "Safe" -> CentinelSafe
    "Low" -> CentinelLow
    "Medium" -> CentinelMedium
    "High" -> CentinelHigh
    "Critical" -> CentinelCritical
    else -> Color.Gray
}

private val LightColors = lightColorScheme(
    primary = CentinelBlue,
    secondary = CentinelDark,
)
private val DarkColors = darkColorScheme(
    primary = CentinelBlue,
    secondary = Color(0xFF93C5FD),
)

@Composable
fun CentinelTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
