package com.centinel.app.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Robust models with default values to prevent deserialization errors when connecting to custom API backends.
 */

@Serializable
data class PasswordScanResponse(
    val id: String = "",
    val scan_type: String = "password",
    val target_summary: String = "",
    val risk_score: Int = 0,
    val threat_level: String = "Safe",
    val confidence: Double = 0.9,
    val detected_threats: List<String> = emptyList(),
    val indicators: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val explanation: String = "",
    val created_at: String = "JUST NOW",
    val details: PasswordDetails? = null,
)

@Serializable
data class PasswordDetails(
    val length: Int = 0,
    val entropy_bits: Double = 0.0,
    val score: Int = 0,
    val strength: String = "Moderate",
    val is_common_password: Boolean = false,
    val has_sequential_pattern: Boolean = false,
    val has_repeated_chars: Boolean = false,
    val dictionary_risk: Boolean = false,
)

@Serializable
data class SslScanResponse(
    val id: String = "",
    val scan_type: String = "ssl",
    val target_summary: String = "",
    val risk_score: Int = 0,
    val threat_level: String = "Safe",
    val confidence: Double = 0.9,
    val detected_threats: List<String> = emptyList(),
    val indicators: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val explanation: String = "",
    val created_at: String = "JUST NOW",
    val certificate: JsonElement? = null,
)

@Serializable
data class BreachScanResponse(
    val id: String = "",
    val scan_type: String = "breach",
    val target_summary: String = "",
    val risk_score: Int = 0,
    val threat_level: String = "Safe",
    val confidence: Double = 0.9,
    val indicators: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val explanation: String = "",
    val created_at: String = "JUST NOW",
    val details: JsonElement? = null,
)

@Serializable
data class HeaderScanResponse(
    val id: String = "",
    val scan_type: String = "header",
    val target_summary: String = "",
    val risk_score: Int = 0,
    val threat_level: String = "Safe",
    val confidence: Double = 0.9,
    val indicators: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val explanation: String = "",
    val created_at: String = "JUST NOW",
    val extracted: JsonElement? = null,
)

@Serializable
data class WebsiteScanResponse(
    val id: String = "",
    val scan_type: String = "website",
    val target_summary: String = "",
    val risk_score: Int = 0,
    val threat_level: String = "Safe",
    val confidence: Double = 0.9,
    val indicators: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val explanation: String = "",
    val created_at: String = "JUST NOW",
    val extracted: JsonElement? = null,
)

@Serializable
data class FileScanResponse(
    val id: String = "",
    val scan_type: String = "file",
    val target_summary: String = "",
    val risk_score: Int = 0,
    val threat_level: String = "Safe",
    val confidence: Double = 0.9,
    val indicators: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val explanation: String = "",
    val created_at: String = "JUST NOW",
    val file_info: JsonElement? = null,
)
