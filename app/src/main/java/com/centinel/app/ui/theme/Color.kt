package com.centinel.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dynamic Theme Color Getters
val Background: Color
    @Composable get() = MaterialTheme.colorScheme.background

val Surface: Color
    @Composable get() = MaterialTheme.colorScheme.surface

val SurfaceVariant: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant

val SurfaceLighter = Color(0xFF1E293B)

// Core Minimalist Blue Palette
val PrimaryBlue = Color(0xFF3B82F6)        // Royal Electric Blue
val PrimaryBlueDark = Color(0xFF1D4ED8)    // Deep Blue
val LightBlue = Color(0xFF60A5FA)          // Soft Sky Blue
val IceBlue = Color(0xFF38BDF8)            // Crisp Ice Blue
val AccentCyan = Color(0xFF0EA5E9)         // Cyan Accent
val PrimaryGlow = Color(0x263B82F6)
val AccentCyanGlow = Color(0x260EA5E9)

// Legacy compatibility aliases
val SecondaryPurple = Color(0xFF6366F1)    // Clean Indigo Blue
val CyberCyan = IceBlue
val CyberPink = LightBlue
val CyberPurple = Color(0xFF6366F1)
val CyberYellow = Color(0xFFF59E0B)
val CyberGreen = Color(0xFF10B981)
val CyberDarkBackground = Color(0xFF0B0F19)

// Module Color Coding (Blue-centric, consistent & subtle)
val ModuleCyan = IceBlue                  // URL / Website / SSL / Network
val ModulePurple = Color(0xFF6366F1)      // Email / Headers
val ModulePink = Color(0xFF0EA5E9)        // SMS / Communications
val ModuleAmber = Color(0xFF0284C7)       // Files / Storage
val ModuleGreen = Color(0xFF10B981)       // Passwords / Security Credentials
val ModuleBlue = PrimaryBlue              // QR Scanner
val ModuleIndigo = Color(0xFF4F46E5)      // Analytics / System

// Functional Status Colors (Used only when status clarity is required)
val SuccessGreen = Color(0xFF10B981)
val DangerRed = Color(0xFFEF4444)
val WarningOrange = Color(0xFFF59E0B)

// Typography
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextTertiary = Color(0xFF64748B)

// Minimalist Glassmorphism Surfaces & Subtle Blue Borders
val GlassDark = Color(0xF20F172A)          // Dark Navy Slate Surface
val GlassLight = Color(0x0DFFFFFF)
val GlassBorder = Color(0x1E38BDF8)        // Subtle Ice-Blue Border

val SubtleBorderGradient = listOf(IceBlue.copy(alpha = 0.2f), PrimaryBlue.copy(alpha = 0.08f))
val ActiveBorderGradient = listOf(IceBlue, PrimaryBlue)
val CyberBorderGradient = listOf(IceBlue.copy(alpha = 0.4f), PrimaryBlue.copy(alpha = 0.2f))

// Unified Blue Gradients
val PremiumGradient = listOf(PrimaryBlue, PrimaryBlueDark)
val CyanGradient = listOf(IceBlue, PrimaryBlue)
val CyberNeonGradient = listOf(IceBlue, LightBlue)
val CyberBluePurpleGradient = listOf(PrimaryBlue, Color(0xFF6366F1))

// Glows
val GlowBlue = Color(0x263B82F6)
val GlowPurple = Color(0x266366F1)
val GlowCyan = Color(0x2638BDF8)
val GlowPink = Color(0x2660A5FA)

fun getModuleColor(routeOrType: String): Color {
    val key = routeOrType.lowercase()
    return when {
        key.contains("url") || key.contains("web") || key.contains("ssl") -> ModuleCyan
        key.contains("email") || key.contains("header") -> ModulePurple
        key.contains("sms") || key.contains("comm") -> ModulePink
        key.contains("file") || key.contains("upload") -> ModuleAmber
        key.contains("password") || key.contains("biometric") -> ModuleGreen
        key.contains("breach") -> DangerRed // Breach alert requires Red status
        key.contains("qr") -> ModuleBlue
        key.contains("analytics") || key.contains("history") || key.contains("notification") -> ModuleIndigo
        else -> PrimaryBlue
    }
}
