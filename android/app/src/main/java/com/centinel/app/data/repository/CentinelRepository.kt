package com.centinel.app.data.repository

import android.content.Context
import com.centinel.app.data.api.ApiService
import com.centinel.app.data.api.RetrofitClient
import com.centinel.app.data.local.TokenStore
import com.centinel.app.data.model.*
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int? = null) : ApiResult<Nothing>()
}

class CentinelRepository(context: Context) {
    val tokenStore = TokenStore(context.applicationContext)
    private val api: ApiService = RetrofitClient.getApiService(tokenStore)

    private suspend fun <T> safe(call: suspend () -> Response<T>): ApiResult<T> {
        return try {
            val resp = call()
            if (resp.isSuccessful) {
                resp.body()?.let { ApiResult.Success(it) }
                    ?: ApiResult.Error("Empty response from server", resp.code())
            } else {
                val errBody = resp.errorBody()?.string()
                val detail = try {
                    errBody?.let { Json { ignoreUnknownKeys = true }.decodeFromString<ApiError>(it).detail }
                } catch (e: Exception) { null }
                ApiResult.Error(detail ?: "Request failed (${resp.code()})", resp.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Network error — is the backend running and reachable?")
        }
    }

    // ---- Auth ----
    suspend fun register(email: String, password: String, fullName: String?) =
        safe { api.register(UserRegisterRequest(email, password, fullName)) }

    suspend fun login(email: String, password: String): ApiResult<TokenPair> {
        val result = safe { api.login(UserLoginRequest(email, password)) }
        if (result is ApiResult.Success) {
            tokenStore.saveTokens(result.data.access_token, result.data.refresh_token)
        }
        return result
    }

    suspend fun logout() {
        try { api.logout() } catch (_: Exception) {}
        tokenStore.clear()
    }

    suspend fun me() = safe { api.me() }

    // ---- Scans ----
    suspend fun scanUrl(url: String) = safe { api.scanUrl(UrlScanRequest(url)) }

    suspend fun scanWebsite(url: String) = safe { api.scanWebsite(WebsiteScanRequest(url)) }

    suspend fun scanEmail(subject: String?, sender: String?, body: String?, rawEmail: String?) =
        safe { api.scanEmail(EmailScanRequest(rawEmail, subject, sender, body)) }

    suspend fun scanEmailFile(fileName: String, bytes: ByteArray) = safe {
        val reqFile = bytes.toRequestBody("message/rfc822".toMediaType())
        val part = MultipartBody.Part.createFormData("file", fileName, reqFile)
        api.scanEmailFile(part)
    }

    suspend fun scanHeader(rawHeaders: String) = safe { api.scanHeader(HeaderScanRequest(rawHeaders)) }

    suspend fun scanSms(message: String, sender: String?) = safe { api.scanSms(SmsScanRequest(message, sender)) }

    suspend fun scanQr(decodedText: String) = safe { api.scanQr(QrScanRequest(decodedText)) }

    suspend fun scanPassword(password: String) = safe { api.scanPassword(PasswordScanRequest(password)) }

    suspend fun scanSsl(hostname: String) = safe { api.scanSsl(SslScanRequest(hostname)) }

    suspend fun scanBreach(email: String) = safe { api.scanBreach(BreachScanRequest(email)) }

    suspend fun scanFile(fileName: String, bytes: ByteArray, mimeType: String = "application/octet-stream") = safe {
        val reqFile = bytes.toRequestBody(mimeType.toMediaType())
        val part = MultipartBody.Part.createFormData("file", fileName, reqFile)
        api.scanFile(part)
    }

    // ---- History / analytics / notifications ----
    suspend fun getHistory(scanType: String? = null, threatLevel: String? = null, search: String? = null) =
        safe { api.getHistory(scanType, threatLevel, search) }

    suspend fun deleteHistoryItem(id: String) = safe { api.deleteHistoryItem(id) }

    suspend fun getAnalytics() = safe { api.getAnalytics() }

    suspend fun getNotifications() = safe { api.getNotifications() }

    suspend fun markNotificationRead(id: String) = safe { api.markNotificationRead(id) }

    // ---- Reports ----
    suspend fun getReportPdf(scanId: String) = safe { api.getReportPdf(scanId) }
}
