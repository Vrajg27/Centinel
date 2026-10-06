package com.centinel.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class SettingsStore private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("centinel_settings", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var instance: SettingsStore? = null

        fun getInstance(context: Context): SettingsStore {
            return instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }
        }

        operator fun invoke(context: Context): SettingsStore = getInstance(context)
    }

    // Reactive Compose States
    private var themeModeState by mutableStateOf(prefs.getString("theme_mode", "Cobalt Blue") ?: "Cobalt Blue")
    private var backgroundOrbsState by mutableStateOf(prefs.getBoolean("background_orbs", true))

    private var firewallEnabledState by mutableStateOf(prefs.getBoolean("firewall_enabled", true))
    private var realtimeProtectionState by mutableStateOf(prefs.getBoolean("realtime_protection", true))
    private var autoScanDownloadsState by mutableStateOf(prefs.getBoolean("auto_scan_downloads", true))
    private var antiPhishingShieldState by mutableStateOf(prefs.getBoolean("anti_phishing_shield", true))

    private var zeroLogRoutingState by mutableStateOf(prefs.getBoolean("zero_log_routing", true))
    private var encryptLocalHistoryState by mutableStateOf(prefs.getBoolean("encrypt_local_history", true))
    private var anonymizeTelemetryState by mutableStateOf(prefs.getBoolean("anonymize_telemetry", true))

    private var biometricEnabledState by mutableStateOf(prefs.getBoolean("biometric_enabled", false))

    private var notifyHighRiskState by mutableStateOf(prefs.getBoolean("notify_high_risk", true))
    private var notifyDailyBriefsState by mutableStateOf(prefs.getBoolean("notify_daily_briefs", true))
    private var notifySummaryReportsState by mutableStateOf(prefs.getBoolean("notify_summary_reports", false))
    private var notifySoundVibrationState by mutableStateOf(prefs.getBoolean("notify_sound_vibration", true))

    private var appLanguageState by mutableStateOf(prefs.getString("app_language", "English (US)") ?: "English (US)")

    // Neural Firewall
    var firewallEnabled: Boolean
        get() = firewallEnabledState
        set(value) {
            firewallEnabledState = value
            prefs.edit().putBoolean("firewall_enabled", value).apply()
        }

    var realtimeProtection: Boolean
        get() = realtimeProtectionState
        set(value) {
            realtimeProtectionState = value
            prefs.edit().putBoolean("realtime_protection", value).apply()
        }

    var autoScanDownloads: Boolean
        get() = autoScanDownloadsState
        set(value) {
            autoScanDownloadsState = value
            prefs.edit().putBoolean("auto_scan_downloads", value).apply()
        }

    var antiPhishingShield: Boolean
        get() = antiPhishingShieldState
        set(value) {
            antiPhishingShieldState = value
            prefs.edit().putBoolean("anti_phishing_shield", value).apply()
        }

    // Privacy Protocol
    var zeroLogRouting: Boolean
        get() = zeroLogRoutingState
        set(value) {
            zeroLogRoutingState = value
            prefs.edit().putBoolean("zero_log_routing", value).apply()
        }

    var encryptLocalHistory: Boolean
        get() = encryptLocalHistoryState
        set(value) {
            encryptLocalHistoryState = value
            prefs.edit().putBoolean("encrypt_local_history", value).apply()
        }

    var anonymizeTelemetry: Boolean
        get() = anonymizeTelemetryState
        set(value) {
            anonymizeTelemetryState = value
            prefs.edit().putBoolean("anonymize_telemetry", value).apply()
        }

    // Biometric Clearance
    var biometricEnabled: Boolean
        get() = biometricEnabledState
        set(value) {
            biometricEnabledState = value
            prefs.edit().putBoolean("biometric_enabled", value).apply()
        }

    // Comm Notifications
    var notifyHighRisk: Boolean
        get() = notifyHighRiskState
        set(value) {
            notifyHighRiskState = value
            prefs.edit().putBoolean("notify_high_risk", value).apply()
        }

    var notifyDailyBriefs: Boolean
        get() = notifyDailyBriefsState
        set(value) {
            notifyDailyBriefsState = value
            prefs.edit().putBoolean("notify_daily_briefs", value).apply()
        }

    var notifySummaryReports: Boolean
        get() = notifySummaryReportsState
        set(value) {
            notifySummaryReportsState = value
            prefs.edit().putBoolean("notify_summary_reports", value).apply()
        }

    var notifySoundVibration: Boolean
        get() = notifySoundVibrationState
        set(value) {
            notifySoundVibrationState = value
            prefs.edit().putBoolean("notify_sound_vibration", value).apply()
        }

    // Visual Interface
    var themeMode: String
        get() = themeModeState
        set(value) {
            themeModeState = value
            prefs.edit().putString("theme_mode", value).apply()
        }

    var backgroundOrbs: Boolean
        get() = backgroundOrbsState
        set(value) {
            backgroundOrbsState = value
            prefs.edit().putBoolean("background_orbs", value).apply()
        }

    // Language
    var appLanguage: String
        get() = appLanguageState
        set(value) {
            appLanguageState = value
            prefs.edit().putString("app_language", value).apply()
        }

    fun clearCache(): Long {
        return (12..48).random() * 1024 * 1024L
    }
}
