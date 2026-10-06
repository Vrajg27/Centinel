package com.centinel.app.data.api

import com.centinel.app.BuildConfig
import com.centinel.app.CentinelConfig
import com.centinel.app.data.local.TokenStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile
    private var apiService: ApiService? = null
    @Volatile
    private var lastBaseUrl: String? = null

    fun getApiService(tokenStore: TokenStore): ApiService {
        val currentUrl = CentinelConfig.getBaseUrl()
        return if (apiService != null && lastBaseUrl == currentUrl) {
            apiService!!
        } else {
            synchronized(this) {
                if (apiService != null && lastBaseUrl == currentUrl) {
                    apiService!!
                } else {
                    lastBaseUrl = currentUrl
                    build(tokenStore, currentUrl).also { apiService = it }
                }
            }
        }
    }

    private fun build(tokenStore: TokenStore, baseUrl: String): ApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }

        val refreshApi = LazyRefreshApi(tokenStore, baseUrl)

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(tokenStore))
            .addInterceptor(logging)
            .authenticator(TokenAuthenticator(tokenStore) { refreshToken ->
                refreshApi.refresh(refreshToken)
            })
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }

    /** Minimal separate Retrofit instance used only for the /auth/refresh call, to avoid recursive auth. */
    private class LazyRefreshApi(private val tokenStore: TokenStore, private val baseUrl: String) {
        private val plainClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .build()

        private val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(plainClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)

        suspend fun refresh(refreshToken: String): String? {
            return try {
                val resp = retrofit.refresh(com.centinel.app.data.model.RefreshRequest(refreshToken))
                if (resp.isSuccessful) {
                    val pair = resp.body() ?: return null
                    tokenStore.saveTokens(pair.access_token, pair.refresh_token)
                    pair.access_token
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
}
