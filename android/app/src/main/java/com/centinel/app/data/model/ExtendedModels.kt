package com.centinel.app.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * A few endpoints (password, ssl, breach, header, file) return the base
 * ScanResult fields PLUS an extra structured object. We model those as
 * "flat" data classes with nullable extra fields rather than needing
 * generics, since JSON structures are known ahead of time.
 */

@Serializable
data class PasswordScanResponse(
    val id: String,
    val scan_type: String,
    val target_summary: String,
    val risk_score: Int,
    val threat_level: String,
    val confidence: Double,
    val detected_threats: List<String> = emptyList(),
    val indicators: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val explanation: String,
    val created_at: String,
    val details: PasswordDetails? = null,
)

@Serializable
data class PasswordDetails(
    val length: Int,
    val entropy_bits: Double,
    val score: Int,
    val strength: String,
    val is_common_password: Boolean,
    val has_sequential_pattern: Boolean,
    val has_repeated_chars: Boolean,
    val dictionary_risk: Boolean,
)

@Serializable
data class SslScanResponse(
    val id: String,
    val scan_type: String,
    val target_summary: String,
    val risk_score: Int,
    val threat_level: String,
    val confidence: Double,
    val detected_threats: List<String> = emptyList(),
    val indicators: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val explanation: String,
    val created_at: String,
    val certificate: JsonElement? = null,
)

@Serializable
data class BreachScanResponse(
    val id: String,
    val scan_type: String,
    val target_summary: String,
    val risk_score: Int,
    val threat_level: String,
    val confidence: Double,
    val indicators: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val explanation: String,
    val created_at: String,
    val details: JsonElement? = null,
)

@Serializable
data class HeaderScanResponse(
    val id: String,
    val scan_type: String,
    val target_summary: String,
    val risk_score: Int,
    val threat_level: String,
    val confidence: Double,
    val indicators: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val explanation: String,
    val created_at: String,
    val extracted: JsonElement? = null,
)

@Serializable
data class WebsiteScanResponse(
    val id: String,
    val scan_type: String,
    val target_summary: String,
    val risk_score: Int,
    val threat_level: String,
    val confidence: Double,
    val indicators: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val explanation: String,
    val created_at: String,
    val extracted: JsonElement? = null,
)

@Serializable
data class FileScanResponse(
    val id: String,
    val scan_type: String,
    val target_summary: String,
    val risk_score: Int,
    val threat_level: String,
    val confidence: Double,
    val indicators: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val explanation: String,
    val created_at: String,
    val file_info: JsonElement? = null,
)
