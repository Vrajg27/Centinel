package com.centinel.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.centinel.app.data.local.SettingsStore

val LocalSettingsStore = staticCompositionLocalOf<SettingsStore?> { null }

val DefaultBackground: Color
    @Composable get() = MaterialTheme.colorScheme.background

val DefaultSurface: Color
    @Composable get() = MaterialTheme.colorScheme.surface

val DefaultSurfaceVariant: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant

private val CrimsonRedColors = darkColorScheme(
    primary = Color(0xFFEF4444),
    secondary = Color(0xFFF87171),
    tertiary = Color(0xFFFCA5A5),
    background = Color(0xFF0F0608),
    surface = Color(0xFF1A0A0C),
    surfaceVariant = Color(0xFF281014),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = DangerRed,
    outline = Color(0xFFEF4444).copy(alpha = 0.3f)
)

private val ObsidianBlackColors = darkColorScheme(
    primary = IceBlue,
    secondary = PrimaryBlue,
    tertiary = LightBlue,
    background = Color(0xFF000000), // Pure Pitch Black for OLED
    surface = Color(0xFF0A0F1A),
    surfaceVariant = Color(0xFF121A2D),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    error = DangerRed,
    outline = GlassBorder
)

private val ElectricPurpleColors = darkColorScheme(
    primary = Color(0xFFA855F7),
    secondary = Color(0xFFC084FC),
    tertiary = Color(0xFFE879F9),
    background = Color(0xFF0D0714),
    surface = Color(0xFF170D24),
    surfaceVariant = Color(0xFF241438),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = DangerRed,
    outline = Color(0xFFA855F7).copy(alpha = 0.3f)
)

private val CobaltBlueColors = darkColorScheme(
    primary = Color(0xFF3B82F6),
    secondary = Color(0xFF60A5FA),
    tertiary = Color(0xFF38BDF8),
    background = Color(0xFF070A12),
    surface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFF1E293B),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = DangerRed,
    outline = GlassBorder
)

private val EmeraldGreenColors = darkColorScheme(
    primary = Color(0xFF10B981),
    secondary = Color(0xFF34D399),
    tertiary = Color(0xFF6EE7B7),
    background = Color(0xFF03140C),
    surface = Color(0xFF082215),
    surfaceVariant = Color(0xFF113824),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color(0xFFECFDF5),
    onSurface = Color(0xFFECFDF5),
    error = DangerRed,
    outline = Color(0xFF10B981).copy(alpha = 0.3f)
)

private val CyberAmberColors = darkColorScheme(
    primary = Color(0xFFF59E0B), // Steampunk Brass / Amber
    secondary = Color(0xFFFBBF24),
    tertiary = Color(0xFFFCD34D),
    background = Color(0xFF120A03), // Steampunk Dark Copper
    surface = Color(0xFF1F1206),
    surfaceVariant = Color(0xFF2E1D0C),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color(0xFFFFFBEB),
    onSurface = Color(0xFFFFFBEB),
    error = DangerRed,
    outline = Color(0xFFF59E0B).copy(alpha = 0.3f)
)

@Composable
fun CentinelTheme(
    settingsStore: SettingsStore? = null,
    content: @Composable () -> Unit
) {
    val themeMode = settingsStore?.themeMode ?: "Cobalt Blue"
    val colorScheme = when (themeMode) {
        "Crimson Red" -> CrimsonRedColors
        "Obsidian Black" -> ObsidianBlackColors
        "Electric Purple" -> ElectricPurpleColors
        "Cobalt Blue" -> CobaltBlueColors
        "Emerald Green" -> EmeraldGreenColors
        "Cyber Amber" -> CyberAmberColors
        else -> CobaltBlueColors
    }

    CompositionLocalProvider(
        LocalSettingsStore provides settingsStore
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CentinelTypography,
            content = content
        )
    }
}

fun threatLevelColor(level: String?): Color = when (level?.lowercase()) {
    "safe", "0" -> SuccessGreen
    "low" -> WarningOrange
    "medium" -> WarningOrange
    "high" -> DangerRed
    "critical" -> DangerRed
    else -> TextSecondary
}
