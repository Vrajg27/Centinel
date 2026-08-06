package com.centinel.app.data.api

import com.centinel.app.data.model.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ---- Auth ----
    @POST("auth/register")
    suspend fun register(@Body body: UserRegisterRequest): Response<UserOut>

    @POST("auth/login")
    suspend fun login(@Body body: UserLoginRequest): Response<TokenPair>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): Response<TokenPair>

    @POST("auth/logout")
    suspend fun logout(): Response<Unit>

    @GET("auth/me")
    suspend fun me(): Response<UserOut>

    // ---- Scans ----
    @POST("scan/url")
    suspend fun scanUrl(@Body body: UrlScanRequest): Response<ScanResult>

    @POST("scan/website")
    suspend fun scanWebsite(@Body body: WebsiteScanRequest): Response<WebsiteScanResponse>

    @POST("scan/email")
    suspend fun scanEmail(@Body body: EmailScanRequest): Response<ScanResult>

    @Multipart
    @POST("scan/email/upload")
    suspend fun scanEmailFile(@Part file: MultipartBody.Part): Response<ScanResult>

    @POST("scan/header")
    suspend fun scanHeader(@Body body: HeaderScanRequest): Response<HeaderScanResponse>

    @POST("scan/sms")
    suspend fun scanSms(@Body body: SmsScanRequest): Response<ScanResult>

    @POST("scan/qr")
    suspend fun scanQr(@Body body: QrScanRequest): Response<ScanResult>

    @POST("scan/password")
    suspend fun scanPassword(@Body body: PasswordScanRequest): Response<PasswordScanResponse>

    @POST("scan/ssl")
    suspend fun scanSsl(@Body body: SslScanRequest): Response<SslScanResponse>

    @POST("scan/breach")
    suspend fun scanBreach(@Body body: BreachScanRequest): Response<BreachScanResponse>

    @Multipart
    @POST("scan/file")
    suspend fun scanFile(@Part file: MultipartBody.Part): Response<FileScanResponse>

    // ---- History ----
    @GET("history")
    suspend fun getHistory(
        @Query("scan_type") scanType: String? = null,
        @Query("threat_level") threatLevel: String? = null,
        @Query("search") search: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): Response<List<ScanResult>>

    @DELETE("history/{id}")
    suspend fun deleteHistoryItem(@Path("id") id: String): Response<Unit>

    // ---- Analytics ----
    @GET("analytics")
    suspend fun getAnalytics(): Response<AnalyticsOut>

    // ---- Notifications ----
    @GET("notifications")
    suspend fun getNotifications(): Response<List<NotificationOut>>

    @POST("notification/read")
    suspend fun markNotificationRead(@Query("notification_id") id: String): Response<Unit>

    // ---- Reports ----
    @GET("report/{id}")
    @Streaming
    suspend fun getReportPdf(@Path("id") id: String): Response<ResponseBody>
}
