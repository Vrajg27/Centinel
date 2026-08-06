package com.centinel.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "centinel_prefs")

/**
 * Stores JWT access/refresh tokens locally on-device. For production
 * hardening, wrap these values with EncryptedSharedPreferences/Keystore
 * instead of plain DataStore.
 */
class TokenStore(private val context: Context) {

    private val ACCESS_TOKEN = stringPreferencesKey("access_token")
    private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")

    val accessTokenFlow: Flow<String?> =
        context.dataStore.data.map { it[ACCESS_TOKEN] }

    suspend fun getAccessToken(): String? = context.dataStore.data.first()[ACCESS_TOKEN]
    suspend fun getRefreshToken(): String? = context.dataStore.data.first()[REFRESH_TOKEN]

    suspend fun saveTokens(access: String, refresh: String) {
        context.dataStore.edit {
            it[ACCESS_TOKEN] = access
            it[REFRESH_TOKEN] = refresh
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun isLoggedIn(): Boolean = getAccessToken() != null
}
