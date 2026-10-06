package com.centinel.app

import androidx.compose.runtime.*

object CentinelConfig {
    /**
     * Runtime flag to enable Admin Test Mode. 
     * Controlled by the "Test Mode" button on the login screen.
     */
    var isAdminTestMode by mutableStateOf(false)

    /**
     * Allows changing the API URL at runtime for local testing.
     */
    var customApiUrl by mutableStateOf<String?>(null)

    fun getBaseUrl(): String = customApiUrl ?: BuildConfig.API_BASE_URL
}
