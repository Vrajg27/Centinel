package com.centinel.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserRegisterRequest(val email: String, val password: String, val full_name: String? = null)

@Serializable
data class UserLoginRequest(val email: String, val password: String)

@Serializable
data class TokenPair(val access_token: String, val refresh_token: String, val token_type: String = "bearer")

@Serializable
data class RefreshRequest(val refresh_token: String)

@Serializable
data class UserOut(val id: String, val email: String, val full_name: String? = null, val is_admin: Boolean = false)

@Serializable
data class ScanResult(
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
)

@Serializable
data class UrlScanRequest(val url: String)

@Serializable
data class WebsiteScanRequest(val url: String)

@Serializable
data class EmailScanRequest(
    val raw_email: String? = null,
    val subject: String? = null,
    val sender: String? = null,
    val body: String? = null,
)

@Serializable
data class HeaderScanRequest(val raw_headers: String)

@Serializable
data class SmsScanRequest(val message: String, val sender: String? = null)

@Serializable
data class PasswordScanRequest(val password: String)

@Serializable
data class SslScanRequest(val hostname: String)

@Serializable
data class QrScanRequest(val decoded_text: String)

@Serializable
data class BreachScanRequest(val email: String)

@Serializable
data class NotificationOut(
    val id: String,
    val title: String,
    val message: String,
    val severity: String,
    val is_read: Boolean,
    val created_at: String,
)

@Serializable
data class AnalyticsOut(
    val total_scans: Int,
    val daily_scans: Int,
    val weekly_scans: Int,
    val monthly_scans: Int,
    val average_risk_score: Double,
    val scans_by_type: Map<String, Int> = emptyMap(),
    val threat_level_distribution: Map<String, Int> = emptyMap(),
    val high_risk_count: Int,
)

@Serializable
data class ApiError(val detail: String? = null)
