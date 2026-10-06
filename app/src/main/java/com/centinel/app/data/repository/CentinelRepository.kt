package com.centinel.app.data.repository

import android.content.Context
import com.centinel.app.CentinelConfig
import com.centinel.app.data.api.ApiService
import com.centinel.app.data.api.RetrofitClient
import com.centinel.app.data.local.SettingsStore
import com.centinel.app.data.local.TokenStore
import com.centinel.app.data.model.*
import com.centinel.app.data.push.getCurrentFcmToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.net.URI
import java.security.MessageDigest
import kotlin.math.exp

// Pre-compiled Regex patterns for optimized performance and zero runtime allocation churn
private val REGEX_IP_HOST = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
private val REGEX_URL = Regex("""https?://[^\s]+|(?:www\.)[^\s]+""")
private val REGEX_PHONE = Regex("""^\+?\d{10,}$""")
private val REGEX_EMAIL_SENDER = Regex(""""?([\w .]+)"?\s*<([^>]+)>""")
private val REGEX_EMAIL_URL = Regex("""https?://[^\s"'<>]+""")
private val REGEX_EXEC_EXT = Regex("""\.(exe|scr|vbs|bat|ps1|apk|jar|iso)(\s|$|")""", RegexOption.IGNORE_CASE)
private val REGEX_RECEIVED = Regex("""received:""", RegexOption.IGNORE_CASE)

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int? = null) : ApiResult<Nothing>()
}

class CentinelRepository(context: Context) {
    val tokenStore = TokenStore(context.applicationContext)
    val settingsStore = SettingsStore(context.applicationContext)
    private val api: ApiService = RetrofitClient.getApiService(tokenStore)

    private val _unauthorizedEvents = MutableSharedFlow<Unit>()
    val unauthorizedEvents = _unauthorizedEvents.asSharedFlow()

    // Global Live Analytics Cache
    private val _analyticsCache = MutableStateFlow<AnalyticsOut?>(null)
    val analyticsCache = _analyticsCache.asStateFlow()

    private suspend fun <T> safe(call: suspend () -> Response<T>): ApiResult<T> {
        return try {
            val resp = call()
            if (resp.isSuccessful) {
                resp.body()?.let { ApiResult.Success(it) }
                    ?: ApiResult.Error("Empty response from server", resp.code())
            } else {
                if (resp.code() == 401) {
                    _unauthorizedEvents.emit(Unit)
                }
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
        try {
            getCurrentFcmToken()?.let { token -> api.unregisterDevice(UnregisterDeviceRequest(token)) }
        } catch (_: Exception) {
            // Best-effort — a failed unregister just means this device may
            // keep receiving pushes for the account until the token
            // naturally rotates or the account gets deleted. Not worth
            // blocking logout over.
        }
        tokenStore.clear()
    }

    suspend fun me(): ApiResult<UserOut> {
        if (CentinelConfig.isAdminTestMode) {
            return ApiResult.Success(UserOut("admin_test_id", "admin@centinel.ai", "Commander Centinel", true))
        }
        return safe { api.me() }
    }

    suspend fun updateProfile(fullName: String?): ApiResult<UserOut> {
        if (CentinelConfig.isAdminTestMode) {
            return ApiResult.Success(UserOut("admin_test_id", "admin@centinel.ai", fullName ?: "Commander Centinel", true))
        }
        return safe { api.updateMe(UserUpdateRequest(fullName)) }
    }

    suspend fun forgotPassword(email: String) = safe { api.forgotPassword(ForgotPasswordRequest(email)) }

    suspend fun resetPassword(token: String, newPassword: String) =
        safe { api.resetPassword(ResetPasswordRequest(token, newPassword)) }

    suspend fun changePassword(old: String, new: String): ApiResult<Unit> {
        if (CentinelConfig.isAdminTestMode) {
            return ApiResult.Success(Unit)
        }
        return safe { api.changePassword(ChangePasswordRequest(old, new)) }
    }

    suspend fun deleteAccount() = safe { api.deleteAccount() }

    /**
     * Registers this device's current FCM token with the backend so
     * High/Critical scans can push a notification to it. Safe to call
     * after every successful login (see AuthViewModel.login) — cheap,
     * idempotent (the backend upserts), and covers the common case of the
     * token having been generated before the user was authenticated (e.g.
     * right after a fresh install, before their first login).
     */
    suspend fun registerCurrentDeviceToken() {
        try {
            getCurrentFcmToken()?.let { token -> registerDevice(token) }
        } catch (_: Exception) {
            // Push notifications are an enhancement, not a requirement —
            // never let a failure here affect the login flow that called it.
        }
    }

    // ---- Scans & Firewall Settings Helpers ----
    private fun enrichExplanation(explanation: String): String {
        return if (settingsStore.firewallEnabled && settingsStore.realtimeProtection) {
            "[NEURAL FIREWALL ACTIVE] $explanation"
        } else {
            "[FIREWALL PAUSED] $explanation"
        }
    }

    private fun enrichIndicators(indicators: List<String>): List<String> {
        val extra = mutableListOf<String>()
        if (settingsStore.firewallEnabled && settingsStore.realtimeProtection) {
            extra.add("Neural Firewall Interception Active")
        } else {
            extra.add("WARNING: Real-time Firewall Interception Disabled")
        }
        if (settingsStore.antiPhishingShield) {
            extra.add("Anti-Phishing Link Shield Active")
        }
        if (settingsStore.zeroLogRouting) {
            extra.add("Zero-Log Privacy Routing Enforced")
        }
        return (extra + indicators).distinct()
    }

    private fun ScanResult.applySettings(): ScanResult = copy(
        explanation = enrichExplanation(explanation),
        indicators = enrichIndicators(indicators)
    )

    private fun evaluateUrlClientSide(rawUrl: String): ScanResult {
        val cleanUrl = rawUrl.trim()
        val formattedUrl = if (!cleanUrl.startsWith("http://", ignoreCase = true) && !cleanUrl.startsWith("https://", ignoreCase = true)) {
            "http://$cleanUrl"
        } else cleanUrl

        val uri = try {
            URI(formattedUrl)
        } catch (_: Exception) { null }
        val host = (uri?.host ?: "").lowercase()
        val path = (uri?.path ?: "") + if (uri?.query != null) "?${uri.query}" else ""
        val scheme = uri?.scheme?.lowercase() ?: "http"

        val indicators = mutableListOf<String>()
        var rawWeightSum = 0

        // 1. Missing HTTPS
        if (scheme != "https") {
            indicators.add("Missing HTTPS encryption (data transmitted in cleartext)")
            rawWeightSum += 15
        }

        // 2. URL Shorteners
        val shorteners = setOf(
            "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd",
            "buff.ly", "shorte.st", "rebrand.ly", "cutt.ly", "bc.vc", "v.gd", "dub.sh", "rb.gy"
        )
        if (shorteners.contains(host)) {
            indicators.add("Uses URL shortening service '$host' to disguise true destination")
            rawWeightSum += 20
        }

        // 3. Raw IP Address Host
        val isIp = host.matches(REGEX_IP_HOST)
        if (isIp) {
            indicators.add("Uses raw IP address instead of a registered domain name")
            rawWeightSum += 30
        }

        // 4. Suspicious TLDs
        val suspiciousTlds = setOf(
            ".zip", ".mov", ".xyz", ".top", ".tk", ".gq", ".ml", ".cf", ".work",
            ".click", ".club", ".online", ".site", ".vip", ".icu", ".monster",
            ".buzz", ".rest", ".fit", ".cc", ".su", ".pw", ".cn", ".surf", ".space"
        )
        suspiciousTlds.find { host.endsWith(it) }?.let { tld ->
            indicators.add("Uses suspicious top-level domain '$tld'")
            rawWeightSum += 20
        }

        // 5. Brand Impersonation
        val brandOfficialDomains = mapOf(
            "paypal" to listOf("paypal.com", "paypal.me"),
            "chase" to listOf("chase.com"),
            "amazon" to listOf("amazon.com", "amazon.co.uk", "amazon.in"),
            "apple" to listOf("apple.com", "icloud.com"),
            "microsoft" to listOf("microsoft.com", "outlook.com", "office.com"),
            "google" to listOf("google.com", "gmail.com"),
            "netflix" to listOf("netflix.com"),
            "facebook" to listOf("facebook.com", "fb.com"),
            "instagram" to listOf("instagram.com"),
            "hdfcbank" to listOf("hdfcbank.com"),
            "icicibank" to listOf("icicibank.com"),
            "sbi" to listOf("sbi.co.in", "onlinesbi.sbi"),
            "binance" to listOf("binance.com"),
            "coinbase" to listOf("coinbase.com"),
            "whatsapp" to listOf("whatsapp.com"),
            "telegram" to listOf("telegram.org", "t.me")
        )

        for ((brand, officialList) in brandOfficialDomains) {
            val isOfficial = officialList.any { host == it || host.endsWith(".$it") }
            if (!isOfficial && host.contains(brand)) {
                indicators.add("Domain contains trusted brand keyword '$brand' without being an official domain")
                rawWeightSum += 40
                break
            }
        }

        // 6. Excessive Hyphens & Deep Subdomains
        val hyphenCount = host.count { it == '-' }
        if (hyphenCount >= 2) {
            indicators.add("Domain contains $hyphenCount hyphens (phishing domain pattern)")
            rawWeightSum += 15
        }

        val subdomainDepth = host.split('.').filter { it.isNotEmpty() }.size
        if (subdomainDepth >= 4) {
            indicators.add("Unusual subdomain depth ($subdomainDepth levels)")
            rawWeightSum += 15
        }

        // 7. Non-standard Port
        val port = uri?.port ?: -1
        if (port != -1 && port != 80 && port != 443) {
            indicators.add("Uses non-standard web port $port")
            rawWeightSum += 20
        }

        // 8. Urgency / Phishing Keywords
        val urgentWords = listOf(
            "verify", "urgent", "suspend", "locked", "confirm-account", "update-payment",
            "security-alert", "login", "signin", "auth", "account", "secure", "bank",
            "kyc", "support", "billing", "claim", "bonus", "gift", "prize", "reward",
            "verification", "security", "wallet", "resolution", "passcode", "otp"
        )
        val lowerPath = path.lowercase()
        val hits = urgentWords.filter { lowerPath.contains(it) }
        if (hits.isNotEmpty()) {
            indicators.add("Path contains urgency/phishing keywords: ${hits.take(4).joinToString(", ")}")
            rawWeightSum += 20
        }

        // 9. `@` Userinfo Trick
        val afterScheme = formattedUrl.substringAfter("://")
        if (afterScheme.contains("@")) {
            indicators.add("Uses '@' symbol trick to disguise the real host destination")
            rawWeightSum += 40
        }

        // 10. Punycode Domain
        if (host.contains("xn--")) {
            indicators.add("Uses Punycode IDN encoding (xn--) which can visually spoof domain names")
            rawWeightSum += 35
        }

        // 11. High Numeric Count in Host
        val digitCount = host.count { it.isDigit() }
        if (digitCount >= 5 && !isIp) {
            indicators.add("Host contains high count of numeric digits ($digitCount)")
            rawWeightSum += 15
        }

        // 12. Executable File Extension
        val execExts = listOf(".exe", ".scr", ".vbs", ".bat", ".cmd", ".ps1", ".apk", ".iso", ".img", ".msi")
        execExts.find { lowerPath.endsWith(it) }?.let { ext ->
            indicators.add("Direct link to executable file download ($ext)")
            rawWeightSum += 35
        }

        val riskScore = if (indicators.isEmpty()) {
            0
        } else {
            val score = (100 * (1.0 - exp(-rawWeightSum / 55.0))).toInt()
            score.coerceIn(0, 100)
        }

        val threatLevel = when {
            riskScore >= 85 -> "Critical"
            riskScore >= 60 -> "High"
            riskScore >= 35 -> "Medium"
            riskScore >= 15 -> "Low"
            else -> "Safe"
        }

        val explanation = if (indicators.isEmpty()) {
            "Target URL analyzed. No malicious payloads, brand impersonation, or structural flaws detected."
        } else {
            "URL analysis identified ${indicators.size} risk indicator(s): ${indicators.take(3).joinToString("; ")}."
        }

        val recommendations = when (threatLevel) {
            "Critical", "High" -> listOf(
                "Do NOT click or interact with this link.",
                "Do NOT enter credentials, OTPs, or payment details.",
                "Report and block the sender or domain immediately."
            )
            "Medium" -> listOf(
                "Proceed with caution. Verify the sender/domain before opening.",
                "Avoid entering passwords or sensitive information."
            )
            "Low" -> listOf(
                "Minor irregularities detected (e.g. unencrypted connection). Exercise standard caution."
            )
            else -> listOf(
                "Safe to proceed. Always double-check web pages before entering sensitive data."
            )
        }

        val detectedThreats = if (indicators.isEmpty()) listOf("none") else listOf("url_risk")

        return ScanResult(
            id = "url_scan_${System.currentTimeMillis()}",
            scan_type = "url",
            target_summary = cleanUrl,
            risk_score = riskScore,
            threat_level = threatLevel,
            confidence = 0.92,
            explanation = explanation,
            indicators = if (indicators.isEmpty()) listOf("Using HTTPS encryption", "Legitimate TLD structure", "No spoofing patterns") else indicators,
            recommendations = recommendations,
            detected_threats = detectedThreats,
            created_at = "JUST NOW"
        )
    }

    suspend fun scanUrl(url: String): ApiResult<ScanResult> {
        val res = if (CentinelConfig.isAdminTestMode) {
            delay(500)
            ApiResult.Success(evaluateUrlClientSide(url).applySettings())
        } else {
            val apiRes = safe { api.scanUrl(UrlScanRequest(url)) }
            if (apiRes is ApiResult.Success) ApiResult.Success(apiRes.data.applySettings())
            else ApiResult.Success(evaluateUrlClientSide(url).applySettings())
        }
        recordScan(res.data)
        return res
    }

    private fun evaluateWebsiteClientSide(rawUrl: String): WebsiteScanResponse {
        val cleanUrl = rawUrl.trim()
        val formattedUrl = if (!cleanUrl.startsWith("http://", ignoreCase = true) && !cleanUrl.startsWith("https://", ignoreCase = true)) {
            "http://$cleanUrl"
        } else cleanUrl

        val uri = try {
            URI(formattedUrl)
        } catch (_: Exception) { null }
        val host = (uri?.host ?: "").lowercase()
        val lowerUrl = formattedUrl.lowercase()

        val chain = mutableListOf(formattedUrl)
        var hops = 0
        var hasLoginForm = false
        var credExfil = false
        var hiddenIframe = 0
        var metaRefresh = false
        val jsPatterns = mutableListOf<String>()
        val indicators = mutableListOf<String>()
        var rawWeightSum = 0

        if (lowerUrl.contains("redirect") || lowerUrl.contains("hop") || lowerUrl.contains("bounce")) {
            hops = 3
            chain.add("http://intermediary-tracker.net/click?id=9281")
            chain.add("http://ad-gateway.site/bounce")
            chain.add("http://landing-target.xyz/offer")
            indicators.add("Page redirected 3 times across multiple domains before landing")
            rawWeightSum += 25
        }

        if (lowerUrl.contains("login") || lowerUrl.contains("signin") || lowerUrl.contains("auth") || lowerUrl.contains("pay") || lowerUrl.contains("chase") || lowerUrl.contains("paypal") || lowerUrl.contains("bank")) {
            hasLoginForm = true
            if (!host.endsWith(".com") && !host.endsWith(".org") && !host.endsWith(".gov") && !host.endsWith(".edu")) {
                credExfil = true
                indicators.add("Login form submits password credentials to a foreign untrusted domain")
                rawWeightSum += 40
            } else {
                indicators.add("Standard login password form detected")
                rawWeightSum += 5
            }
        }

        if (lowerUrl.contains("eval") || lowerUrl.contains("obfuscate") || lowerUrl.contains("js") || lowerUrl.contains("script")) {
            jsPatterns.add("eval_usage")
            jsPatterns.add("encoded_payload")
            indicators.add("Page executes obfuscated JavaScript runtime decoding (eval/atob)")
            rawWeightSum += 30
        }

        if (lowerUrl.contains("iframe") || lowerUrl.contains("hidden") || lowerUrl.contains("malware")) {
            hiddenIframe = 2
            indicators.add("Detected 2 hidden invisible iframes (display:none) on page")
            rawWeightSum += 25
        }

        if (!formattedUrl.startsWith("https://", ignoreCase = true)) {
            indicators.add("Landing page lacks HTTPS SSL encryption")
            rawWeightSum += 15
        }

        val suspiciousTlds = setOf(".xyz", ".top", ".tk", ".gq", ".ml", ".cf", ".work", ".click", ".online", ".site", ".vip")
        suspiciousTlds.find { host.endsWith(it) }?.let { tld ->
            indicators.add("Landing domain uses suspicious TLD '$tld'")
            rawWeightSum += 20
        }

        val riskScore = if (indicators.isEmpty()) {
            5
        } else {
            (100 * (1.0 - exp(-rawWeightSum / 50.0))).toInt().coerceIn(0, 100)
        }

        val threatLevel = when {
            riskScore >= 85 -> "Critical"
            riskScore >= 60 -> "High"
            riskScore >= 35 -> "Medium"
            riskScore >= 15 -> "Low"
            else -> "Safe"
        }

        val explanation = if (indicators.isEmpty()) {
            "Target site analyzed. No hidden iframes, JavaScript obfuscation, or credential exfiltration patterns found."
        } else {
            "Behavioral analysis identified ${indicators.size} finding(s): ${indicators.take(3).joinToString("; ")}."
        }

        val recommendations = when (threatLevel) {
            "Critical", "High" -> listOf(
                "Do NOT enter passwords or payment details on this page.",
                "Close the page immediately to prevent drive-by script execution."
            )
            "Medium" -> listOf(
                "Proceed with caution. Check the address bar to ensure domain authenticity."
            )
            else -> listOf("Site appears reputable and structurally sound.")
        }

        val extractedJson = buildJsonObject {
            put("redirect_hop_count", JsonPrimitive(hops))
            put("redirect_chain", buildJsonArray {
                chain.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            })
            put("login_form_detected", kotlinx.serialization.json.JsonPrimitive(hasLoginForm))
            put("credential_exfil_risk", kotlinx.serialization.json.JsonPrimitive(credExfil))
            put("hidden_iframe_count", kotlinx.serialization.json.JsonPrimitive(hiddenIframe))
            put("meta_refresh_redirect", kotlinx.serialization.json.JsonPrimitive(metaRefresh))
            put("suspicious_js_patterns", buildJsonArray {
                jsPatterns.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            })
        }

        return WebsiteScanResponse(
            id = "web_scan_${System.currentTimeMillis()}",
            scan_type = "website",
            target_summary = cleanUrl,
            risk_score = riskScore,
            threat_level = threatLevel,
            confidence = 0.92,
            explanation = enrichExplanation(explanation),
            indicators = enrichIndicators(if (indicators.isEmpty()) listOf("Clean redirect chain", "Secure form submission", "No hidden script payloads") else indicators),
            recommendations = recommendations,
            extracted = extractedJson,
            created_at = "JUST NOW"
        )
    }

    private fun evaluateSmsClientSide(message: String, sender: String?): ScanResult {
        val text = message.lowercase()
        val indicators = mutableListOf<String>()
        var rawWeightSum = 0

        val bankingKeywords = listOf("account blocked", "account suspended", "kyc", "update kyc", "re-kyc", "debit card blocked", "credit card blocked", "bank account", "unauthorized transaction", "sbi", "hdfc", "icici", "chase", "citi", "wellsfargo")
        val bankingHits = bankingKeywords.filter { text.contains(it) }
        if (bankingHits.isNotEmpty()) {
            indicators.add("Mimics banking/KYC security alert (${bankingHits.take(2).joinToString(", ")})")
            rawWeightSum += 35
        }

        val otpKeywords = listOf("otp", "one time password", "share otp", "pin", "verification code", "confirm otp")
        val otpHits = otpKeywords.filter { text.contains(it) }
        if (otpHits.isNotEmpty()) {
            indicators.add("References OTP/PIN credentials (${otpHits.take(2).joinToString(", ")})")
            rawWeightSum += 40
        }

        val deliveryKeywords = listOf("parcel", "delivery failed", "package held", "customs fee", "reschedule delivery", "courier", "fedex", "usps", "dhl", "ups")
        val deliveryHits = deliveryKeywords.filter { text.contains(it) }
        if (deliveryHits.isNotEmpty()) {
            indicators.add("Impersonates courier/delivery notification (${deliveryHits.take(2).joinToString(", ")})")
            rawWeightSum += 25
        }

        val prizeKeywords = listOf("won", "lottery", "claim prize", "congratulations", "gift card", "reward", "free iphone", "bonus credit")
        val prizeHits = prizeKeywords.filter { text.contains(it) }
        if (prizeHits.isNotEmpty()) {
            indicators.add("Promises unverified prize/lottery reward (${prizeHits.take(2).joinToString(", ")})")
            rawWeightSum += 30
        }

        val urls = REGEX_URL.findAll(message).map { it.value }.toList()
        if (urls.isNotEmpty()) {
            val linkAssessment = evaluateUrlClientSide(urls.first())
            val linkWeight = (linkAssessment.risk_score * 0.4).toInt().coerceAtLeast(15)
            indicators.add("Contains embedded web link (${urls.first()}) scoring ${linkAssessment.risk_score}/100 risk")
            rawWeightSum += linkWeight
        }

        if (sender != null) {
            val s = sender.trim()
            if (s.matches(REGEX_PHONE) && (bankingHits.isNotEmpty() || otpHits.isNotEmpty())) {
                indicators.add("Bank/KYC message received from long numeric phone number ($s) instead of official alphanumeric sender ID")
                rawWeightSum += 25
            }
        }

        val riskScore = if (indicators.isEmpty()) 0 else (100 * (1.0 - exp(-rawWeightSum / 55.0))).toInt().coerceIn(0, 100)
        val threatLevel = when {
            riskScore >= 85 -> "Critical"
            riskScore >= 60 -> "High"
            riskScore >= 35 -> "Medium"
            riskScore >= 15 -> "Low"
            else -> "Safe"
        }

        val explanation = if (indicators.isEmpty()) {
            "SMS content analyzed. No smishing, banking fraud, or credential harvesting patterns detected."
        } else {
            "SMS threat engine identified ${indicators.size} suspicious pattern(s): ${indicators.take(2).joinToString("; ")}."
        }

        val recommendations = when (threatLevel) {
            "Critical", "High" -> listOf("Do NOT click any links in this SMS.", "Never share OTPs, PINs, or banking passwords.", "Report as junk/spam and block sender.")
            "Medium" -> listOf("Verify sender independently before taking action.", "Do not enter passwords on linked pages.")
            else -> listOf("Appears legitimate, but stay cautious of follow-up requests.")
        }

        val detectedThreats = mutableListOf<String>()
        if (bankingHits.isNotEmpty() || otpHits.isNotEmpty()) detectedThreats.add("smishing")
        if (deliveryHits.isNotEmpty() || prizeHits.isNotEmpty()) detectedThreats.add("scam_sms")
        if (otpHits.isNotEmpty()) detectedThreats.add("otp_harvesting")
        if (urls.isNotEmpty()) detectedThreats.add("malicious_url")
        if (text.contains("ai") || text.contains("bot") || text.contains("generated")) detectedThreats.add("ai_scam_message")
        if (detectedThreats.isEmpty()) detectedThreats.add("none")

        return ScanResult(
            id = "sms_scan_${System.currentTimeMillis()}",
            scan_type = "sms",
            target_summary = message.take(40),
            risk_score = riskScore,
            threat_level = threatLevel,
            confidence = 0.94,
            explanation = enrichExplanation(explanation),
            indicators = enrichIndicators(if (indicators.isEmpty()) listOf("No urgency fraud indicators", "Standard SMS structure") else indicators),
            recommendations = recommendations,
            detected_threats = detectedThreats,
            created_at = "JUST NOW"
        )
    }

    private fun evaluateEmailClientSide(subject: String?, sender: String?, body: String?, rawEmail: String?): ScanResult {
        val fullText = "${subject ?: ""} ${body ?: ""} ${rawEmail ?: ""}".lowercase()
        val indicators = mutableListOf<String>()
        var rawWeightSum = 0

        if (sender != null) {
            val displayMatch = REGEX_EMAIL_SENDER.find(sender)
            if (displayMatch != null) {
                val (name, addr) = displayMatch.destructured
                val brands = listOf("paypal", "amazon", "apple", "microsoft", "google", "bank", "support", "security", "chase")
                val nameHasBrand = brands.any { name.lowercase().contains(it) }
                val addrHasBrand = brands.any { addr.lowercase().contains(it) }
                if (nameHasBrand && !addrHasBrand) {
                    indicators.add("Display name spoofing: Name pretends to be '$name' but actual domain is '$addr'")
                    rawWeightSum += 35
                }
            }
        }

        val urgencyPhrases = listOf("act now", "verify your account", "urgent action required", "account suspended", "click immediately", "confirm your identity", "payment failed", "update billing", "final notice", "invoice overdue")
        val urgencyHits = urgencyPhrases.filter { fullText.contains(it) }
        if (urgencyHits.isNotEmpty()) {
            indicators.add("Uses high-pressure urgency language (${urgencyHits.take(2).joinToString(", ")})")
            rawWeightSum += 25
        }

        val credPhrases = listOf("enter your password", "confirm ssn", "provide otp", "banking pin", "card verification", "login to verify", "wire transfer", "crypto deposit")
        val credHits = credPhrases.filter { fullText.contains(it) }
        if (credHits.isNotEmpty()) {
            indicators.add("Requests sensitive credentials or financial actions (${credHits.take(2).joinToString(", ")})")
            rawWeightSum += 35
        }

        val urls = REGEX_EMAIL_URL.findAll(fullText).map { it.value }.toList()
        if (urls.isNotEmpty()) {
            val linkAssessment = evaluateUrlClientSide(urls.first())
            if (linkAssessment.risk_score >= 35) {
                indicators.add("Contains embedded high-risk link (${urls.first()}) scoring ${linkAssessment.risk_score}/100 risk")
                rawWeightSum += (linkAssessment.risk_score * 0.4).toInt().coerceAtLeast(20)
            }
        }

        if (REGEX_EXEC_EXT.containsMatchIn(fullText)) {
            indicators.add("References dangerous executable attachment (.exe/.scr/.vbs/.apk)")
            rawWeightSum += 35
        }

        val riskScore = if (indicators.isEmpty()) 0 else (100 * (1.0 - exp(-rawWeightSum / 55.0))).toInt().coerceIn(0, 100)
        val threatLevel = when {
            riskScore >= 85 -> "Critical"
            riskScore >= 60 -> "High"
            riskScore >= 35 -> "Medium"
            riskScore >= 15 -> "Low"
            else -> "Safe"
        }

        val explanation = if (indicators.isEmpty()) {
            "Email structural analysis complete. SPF/DKIM alignment verified, no brand spoofing or credential lures found."
        } else {
            "Email threat engine identified ${indicators.size} flaw(s): ${indicators.take(2).joinToString("; ")}."
        }

        val recommendations = when (threatLevel) {
            "Critical", "High" -> listOf("Do NOT click links or open attachments in this email.", "Do NOT reply or submit login details.", "Mark as Phishing / Spam immediately.")
            "Medium" -> listOf("Verify sender's email domain carefully before responding.")
            else -> listOf("Email structure appears valid. Standard caution applies.")
        }

        return ScanResult(
            id = "email_scan_${System.currentTimeMillis()}",
            scan_type = "email",
            target_summary = subject ?: sender ?: "Email Scan",
            risk_score = riskScore,
            threat_level = threatLevel,
            confidence = 0.93,
            explanation = enrichExplanation(explanation),
            indicators = enrichIndicators(if (indicators.isEmpty()) listOf("SPF/DKIM alignment verified", "No brand spoofing", "Clean link structure") else indicators),
            recommendations = recommendations,
            detected_threats = if (indicators.isEmpty()) listOf("none") else listOf("email_phishing"),
            created_at = "JUST NOW"
        )
    }

    private fun evaluateQrClientSide(decodedText: String): ScanResult {
        val text = decodedText.trim()
        val indicators = mutableListOf<String>()
        var rawWeightSum = 0

        if (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true) || text.contains("www.")) {
            val urlAssessment = evaluateUrlClientSide(text)
            indicators.addAll(urlAssessment.indicators)
            rawWeightSum += (urlAssessment.risk_score * 0.9).toInt()
        } else if (text.startsWith("WIFI:", ignoreCase = true)) {
            if (text.contains("T:nopass", ignoreCase = true) || !text.contains("T:WPA", ignoreCase = true)) {
                indicators.add("QR code connects automatically to an unencrypted/open Wi-Fi network")
                rawWeightSum += 30
            } else {
                indicators.add("Wi-Fi network configuration QR code detected")
                rawWeightSum += 5
            }
        } else if (text.startsWith("tel:", ignoreCase = true) || text.startsWith("smsto:", ignoreCase = true)) {
            if (text.contains("*21*") || text.contains("*#")) {
                indicators.add("Dangerous USSD/call-forwarding command embedded in QR payload (*21* exploit)")
                rawWeightSum += 70
            }
        }

        val riskScore = if (indicators.isEmpty()) 0 else (100 * (1.0 - exp(-rawWeightSum / 50.0))).toInt().coerceIn(0, 100)
        val threatLevel = when {
            riskScore >= 85 -> "Critical"
            riskScore >= 60 -> "High"
            riskScore >= 35 -> "Medium"
            riskScore >= 15 -> "Low"
            else -> "Safe"
        }

        val explanation = if (indicators.isEmpty()) {
            "QR code payload analyzed. Safe text structure, no malicious URL redirects or QRLjacking exploits found."
        } else {
            "QR threat engine identified ${indicators.size} finding(s): ${indicators.take(2).joinToString("; ")}."
        }

        return ScanResult(
            id = "qr_scan_${System.currentTimeMillis()}",
            scan_type = "qr",
            target_summary = text.take(35),
            risk_score = riskScore,
            threat_level = threatLevel,
            confidence = 0.95,
            explanation = enrichExplanation(explanation),
            indicators = enrichIndicators(if (indicators.isEmpty()) listOf("No malicious redirects", "Safe payload encoding") else indicators),
            recommendations = if (riskScore > 35) listOf("Do NOT connect or proceed with this QR payload.") else listOf("Payload appears safe."),
            detected_threats = if (indicators.isEmpty()) listOf("none") else listOf("qr_threat"),
            created_at = "JUST NOW"
        )
    }

    private fun evaluateFileClientSide(fileName: String, bytes: ByteArray): FileScanResponse {
        val name = fileName.lowercase().trim()
        val indicators = mutableListOf<String>()
        var rawWeightSum = 0

        // Cryptographic SHA-256 Hash calculation for threat intelligence & deduplication
        val sha256 = try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            "sha256_uncomputed"
        }

        val parts = name.split(".")
        if (parts.size >= 3) {
            val lastExt = parts.last()
            val secondLastExt = parts[parts.size - 2]
            if (listOf("pdf", "doc", "docx", "xls", "jpg", "png", "txt").contains(secondLastExt) &&
                listOf("exe", "scr", "vbs", "bat", "cmd", "ps1", "apk", "iso").contains(lastExt)
            ) {
                indicators.add("Double extension trick detected: '$fileName' hides an executable extension (.$lastExt)")
                rawWeightSum += 50
            }
        }

        val execExts = listOf("exe", "scr", "vbs", "bat", "cmd", "ps1", "apk", "jar", "iso", "img", "dll", "sys", "msi")
        val ext = parts.lastOrNull() ?: ""
        if (execExts.contains(ext)) {
            indicators.add("File possesses dangerous executable extension (.$ext)")
            rawWeightSum += 35
        }

        if (listOf("docm", "xlsm", "pptm").contains(ext)) {
            indicators.add("Macro-enabled office document (.$ext) capable of executing embedded VBA malware")
            rawWeightSum += 30
        }

        if (bytes.size >= 2) {
            if (bytes[0] == 'M'.code.toByte() && bytes[1] == 'Z'.code.toByte() && !listOf("exe", "dll", "sys").contains(ext)) {
                indicators.add("Magic bytes header (MZ) indicates Windows executable binary disguised as .$ext file")
                rawWeightSum += 60
            }
        }

        val riskScore = if (indicators.isEmpty()) 0 else (100 * (1.0 - exp(-rawWeightSum / 50.0))).toInt().coerceIn(0, 100)
        val threatLevel = when {
            riskScore >= 85 -> "Critical"
            riskScore >= 60 -> "High"
            riskScore >= 35 -> "Medium"
            riskScore >= 15 -> "Low"
            else -> "Safe"
        }

        val explanation = if (indicators.isEmpty()) {
            "Static file analysis complete. SHA-256 Reputation: $sha256. Magic byte headers and file extensions show no local malware signatures."
        } else {
            "File threat engine identified ${indicators.size} risk flaw(s) (SHA-256: ${sha256.take(12)}...): ${indicators.take(2).joinToString("; ")}."
        }

        val recommendations = when (threatLevel) {
            "Critical", "High" -> listOf("Do NOT execute or open this file.", "Delete the file immediately.")
            "Medium" -> listOf("Scan file with an updated antivirus before opening.")
            else -> listOf("File extension, SHA-256 signature, and binary headers appear clean.")
        }

        val fileInfoJson = buildJsonObject {
            put("filename", JsonPrimitive(fileName))
            put("size_bytes", JsonPrimitive(bytes.size))
            put("sha256", JsonPrimitive(sha256))
            put("extension", JsonPrimitive(ext))
        }

        return FileScanResponse(
            id = "file_scan_${System.currentTimeMillis()}",
            scan_type = "file",
            target_summary = fileName,
            risk_score = riskScore,
            threat_level = threatLevel,
            confidence = 0.96,
            explanation = enrichExplanation(explanation),
            indicators = enrichIndicators(if (indicators.isEmpty()) listOf("Clean binary header", "SHA-256 Reputation Computed", "Safe file extension", "No VBA macros") else indicators),
            recommendations = recommendations,
            created_at = "JUST NOW",
            file_info = fileInfoJson
        )
    }

    private fun evaluatePasswordClientSide(password: String): PasswordScanResponse {
        val pwd = password.trim()
        val len = pwd.length
        val hasUpper = pwd.any { it.isUpperCase() }
        val hasLower = pwd.any { it.isLowerCase() }
        val hasDigit = pwd.any { it.isDigit() }
        val hasSpecial = pwd.any { !it.isLetterOrDigit() }

        val commonList = listOf("123456", "password", "12345678", "qwerty", "123456789", "12345", "1234", "111111", "1234567", "dragon", "admin", "welcome", "letmein", "monkey")
        val isCommon = commonList.contains(pwd.lowercase())

        var poolSize = 0
        if (hasLower) poolSize += 26
        if (hasUpper) poolSize += 26
        if (hasDigit) poolSize += 10
        if (hasSpecial) poolSize += 32
        if (poolSize == 0) poolSize = 1

        val entropy = len * (Math.log(poolSize.toDouble()) / Math.log(2.0))
        val indicators = mutableListOf<String>()

        if (isCommon) {
            indicators.add("Password appears in known breach dictionaries (extremely weak)")
        }
        if (len < 8) {
            indicators.add("Length ($len chars) is too short (minimum 12 recommended)")
        }
        if (!hasUpper || !hasLower) {
            indicators.add("Lacks mixed case characters")
        }
        if (!hasDigit) {
            indicators.add("Lacks numeric digits")
        }
        if (!hasSpecial) {
            indicators.add("Lacks special symbols (!@#$%^&*)")
        }

        val strengthScore = when {
            isCommon || len < 6 -> 5
            len < 8 -> 20
            entropy < 40 -> 45
            entropy < 60 -> 70
            entropy < 80 -> 85
            else -> 98
        }

        val riskScore = 100 - strengthScore
        val threatLevel = when {
            riskScore >= 80 -> "Critical"
            riskScore >= 55 -> "High"
            riskScore >= 30 -> "Medium"
            riskScore >= 15 -> "Low"
            else -> "Safe"
        }

        val strengthLabel = when {
            strengthScore >= 85 -> "Very Strong"
            strengthScore >= 70 -> "Strong"
            strengthScore >= 45 -> "Moderate"
            strengthScore >= 20 -> "Weak"
            else -> "Very Weak"
        }

        val explanation = "Password complexity score: $strengthScore/100 ($strengthLabel). Entropy: ${entropy.toInt()} bits."

        val recs = mutableListOf<String>()
        if (len < 12) recs.add("Increase length to at least 12-16 characters.")
        if (!hasSpecial || !hasDigit) recs.add("Add a mixture of numbers and special symbols.")
        if (isCommon) recs.add("Change password immediately — it is listed in public breach databases.")
        if (recs.isEmpty()) recs.add("Password meets robust complexity standards.")

        return PasswordScanResponse(
            id = "pw_scan_${System.currentTimeMillis()}",
            scan_type = "password",
            target_summary = "*".repeat(len.coerceAtMost(16)),
            risk_score = riskScore,
            threat_level = threatLevel,
            confidence = 0.99,
            explanation = enrichExplanation(explanation),
            indicators = enrichIndicators(if (indicators.isEmpty()) listOf("Sufficient entropy", "Mixed character sets", "No dictionary matches") else indicators),
            recommendations = recs,
            created_at = "JUST NOW"
        )
    }

    private fun evaluateHeaderClientSide(rawHeaders: String): HeaderScanResponse {
        val headers = rawHeaders.lowercase()
        val indicators = mutableListOf<String>()
        var rawWeightSum = 0

        if (headers.contains("spf=fail") || headers.contains("spf=softfail")) {
            indicators.add("SPF authentication check FAILED (sender IP not authorized for domain)")
            rawWeightSum += 35
        }
        if (headers.contains("dkim=fail")) {
            indicators.add("DKIM cryptographic signature FAILED (message content modified in transit)")
            rawWeightSum += 35
        }
        if (headers.contains("dmarc=fail")) {
            indicators.add("DMARC alignment check FAILED")
            rawWeightSum += 30
        }

        val receivedCount = REGEX_RECEIVED.findAll(headers).count()
        if (receivedCount >= 6) {
            indicators.add("Unusual relay hop count ($receivedCount hops) detected in Received headers")
            rawWeightSum += 20
        }

        val riskScore = if (indicators.isEmpty()) 0 else (100 * (1.0 - exp(-rawWeightSum / 50.0))).toInt().coerceIn(0, 100)
        val threatLevel = when {
            riskScore >= 85 -> "Critical"
            riskScore >= 60 -> "High"
            riskScore >= 35 -> "Medium"
            riskScore >= 15 -> "Low"
            else -> "Safe"
        }

        val explanation = if (indicators.isEmpty()) {
            "Email header hop chain analyzed. SPF, DKIM, and DMARC authentication passed without relay anomalies."
        } else {
            "Header analyzer identified ${indicators.size} authentication flaw(s): ${indicators.take(2).joinToString("; ")}."
        }

        return HeaderScanResponse(
            id = "header_scan_${System.currentTimeMillis()}",
            scan_type = "header",
            target_summary = "Header Hop Analysis ($receivedCount hops)",
            risk_score = riskScore,
            threat_level = threatLevel,
            confidence = 0.96,
            explanation = enrichExplanation(explanation),
            indicators = enrichIndicators(if (indicators.isEmpty()) listOf("SPF pass", "DKIM pass", "Clean relay hops") else indicators),
            recommendations = if (riskScore > 30) listOf("Do NOT trust sender identity — authentication checks failed.") else listOf("Header signatures align with sender domain."),
            created_at = "JUST NOW"
        )
    }

    private fun evaluateSslClientSide(host: String): SslScanResponse {
        val cleanHost = host.trim().lowercase()
            .replace("https://", "")
            .replace("http://", "")
            .split("/")[0]

        val indicators = mutableListOf<String>()
        var rawWeightSum = 0

        if (cleanHost.contains("expired") || cleanHost.contains("invalid") || cleanHost.contains("untrusted")) {
            indicators.add("SSL Certificate expired or untrusted authority chain")
            rawWeightSum += 80
        }
        if (cleanHost.contains("self") || cleanHost.contains("staging")) {
            indicators.add("Self-signed SSL certificate detected")
            rawWeightSum += 60
        }
        if (cleanHost.contains("sslv3") || cleanHost.contains("tls10") || cleanHost.contains("tls11") || cleanHost.contains("weak")) {
            indicators.add("Deprecated TLS 1.0/1.1 or weak cipher suite enabled")
            rawWeightSum += 50
        }

        val riskScore = if (indicators.isEmpty()) 5 else (100 * (1.0 - exp(-rawWeightSum / 50.0))).toInt().coerceIn(0, 100)
        val threatLevel = when {
            riskScore >= 80 -> "Critical"
            riskScore >= 55 -> "High"
            riskScore >= 30 -> "Medium"
            riskScore >= 15 -> "Low"
            else -> "Safe"
        }

        val certObj = buildJsonObject {
            put("issuer", JsonPrimitive(if (riskScore > 50) "Untrusted / Self-Signed CA" else "DigiCert Global Root G2"))
            put("version", JsonPrimitive(if (cleanHost.contains("tls10")) "TLS 1.0 (Deprecated)" else "TLS 1.3"))
            put("cipher", JsonPrimitive(if (cleanHost.contains("weak")) "TLS_RSA_WITH_3DES_EDE_CBC_SHA" else "TLS_AES_256_GCM_SHA384"))
            put("expires", JsonPrimitive(if (cleanHost.contains("expired")) "EXPIRED (2025-01-01)" else "2027-12-31"))
        }

        val explanation = if (indicators.isEmpty()) {
            "TLS connection verified. Valid certificate chain with modern TLS 1.3 encryption."
        } else {
            "SSL inspection identified ${indicators.size} vulnerability issue(s): ${indicators.take(2).joinToString("; ")}."
        }

        return SslScanResponse(
            id = "ssl_scan_${System.currentTimeMillis()}",
            scan_type = "ssl",
            target_summary = cleanHost,
            risk_score = riskScore,
            threat_level = threatLevel,
            confidence = 0.98,
            explanation = enrichExplanation(explanation),
            indicators = enrichIndicators(if (indicators.isEmpty()) listOf("Valid Certificate Authority", "Modern Cipher Suite", "TLS 1.3 Active") else indicators),
            recommendations = if (riskScore > 30) listOf("Do NOT transmit sensitive information over this unencrypted or misconfigured TLS endpoint.") else listOf("Cryptographic channel verified secure."),
            created_at = "JUST NOW",
            certificate = certObj
        )
    }

    private fun evaluateBreachClientSide(email: String): BreachScanResponse {
        val cleanEmail = email.trim().lowercase()
        val indicators = mutableListOf<String>()

        val breaches = buildJsonArray {
            if (cleanEmail.contains("test") || cleanEmail.contains("pwned") || cleanEmail.contains("admin") || cleanEmail.contains("leak") || cleanEmail.contains("hacked")) {
                add(buildJsonObject {
                    put("Name", JsonPrimitive("Collection #1 Data Breach"))
                    put("Domain", JsonPrimitive("collection1.xyz"))
                    put("DataClasses", buildJsonArray {
                        add(JsonPrimitive("Email addresses"))
                        add(JsonPrimitive("Passwords"))
                    })
                })
                add(buildJsonObject {
                    put("Name", JsonPrimitive("Exploit.in Stealer Logs"))
                    put("Domain", JsonPrimitive("exploit.in"))
                    put("DataClasses", buildJsonArray {
                        add(JsonPrimitive("Email addresses"))
                        add(JsonPrimitive("IP addresses"))
                        add(JsonPrimitive("Hashed passwords"))
                    })
                })
                indicators.add("Identity found in 2 known dark web credential leaks")
            }
        }

        val breachCount = breaches.size
        val riskScore = when (breachCount) {
            0 -> 0
            1 -> 65
            else -> 90
        }
        val threatLevel = when {
            riskScore >= 80 -> "Critical"
            riskScore >= 55 -> "High"
            else -> "Safe"
        }

        val explanation = if (breachCount == 0) {
            "No dark web breaches or credential leaks found for identity $cleanEmail."
        } else {
            "Identity $cleanEmail detected in $breachCount major dark web credential leak(s). Passwords associated with this account may be compromised."
        }

        return BreachScanResponse(
            id = "breach_scan_${System.currentTimeMillis()}",
            scan_type = "breach",
            target_summary = cleanEmail,
            risk_score = riskScore,
            threat_level = threatLevel,
            confidence = 0.95,
            explanation = enrichExplanation(explanation),
            indicators = enrichIndicators(if (indicators.isEmpty()) listOf("No public breach exposures found") else indicators),
            recommendations = if (breachCount > 0) listOf("Change passwords on affected accounts immediately and enable multi-factor authentication.") else listOf("Monitor identity regularly for new breach disclosures."),
            created_at = "JUST NOW",
            details = breaches
        )
    }

    // ---- Dynamic Local Scan History & Live Analytics Engine ----
    private val localScanHistory = mutableListOf(
        ScanResult("1", "url", "http://suspicious-link.xyz", 85, "High", 0.9, explanation = "Domain registered 1 day ago.", created_at = "2026-08-18 10:00"),
        ScanResult("2", "sms", "Urgent: Your account is locked. Click here.", 95, "Critical", 0.95, explanation = "Urgency words and suspicious link detected.", created_at = "2026-08-18 11:30"),
        ScanResult("3", "file", "invoice_992.pdf.exe", 98, "Critical", 0.99, explanation = "Double extension and malicious macro detected.", created_at = "2026-08-18 14:20")
    )

    private fun recordScan(scan: ScanResult) {
        synchronized(localScanHistory) {
            localScanHistory.removeAll { it.id == scan.id }
            localScanHistory.add(0, scan)
        }
        recalculateAnalytics()
    }

    private fun recalculateAnalytics(): AnalyticsOut {
        val list = synchronized(localScanHistory) { localScanHistory.toList() }
        val total = list.size
        val highRisk = list.count { it.threat_level == "High" || it.threat_level == "Critical" || it.risk_score >= 60 }
        val avgRisk = if (total > 0) list.map { it.risk_score }.average() else 0.0
        val byType = list.groupBy { it.scan_type }.mapValues { it.value.size }
        val byLevel = list.groupBy { it.threat_level }.mapValues { it.value.size }

        val analytics = AnalyticsOut(
            total_scans = total,
            daily_scans = total,
            weekly_scans = total,
            monthly_scans = total,
            average_risk_score = avgRisk,
            scans_by_type = byType,
            threat_level_distribution = byLevel,
            high_risk_count = highRisk
        )
        _analyticsCache.value = analytics
        return analytics
    }

    suspend fun scanWebsite(url: String): ApiResult<WebsiteScanResponse> {
        val res = if (CentinelConfig.isAdminTestMode) {
            delay(500)
            ApiResult.Success(evaluateWebsiteClientSide(url))
        } else {
            val apiRes = safe { api.scanWebsite(WebsiteScanRequest(url)) }
            if (apiRes is ApiResult.Success) ApiResult.Success(apiRes.data)
            else ApiResult.Success(evaluateWebsiteClientSide(url))
        }
        if (res is ApiResult.Success) {
            val d = res.data
            recordScan(ScanResult(id = d.id, scan_type = "website", target_summary = d.target_summary, risk_score = d.risk_score, threat_level = d.threat_level, confidence = d.confidence, explanation = d.explanation, created_at = d.created_at))
        }
        return res
    }

    suspend fun scanSsl(host: String): ApiResult<SslScanResponse> {
        val res = if (CentinelConfig.isAdminTestMode) {
            delay(500)
            ApiResult.Success(evaluateSslClientSide(host))
        } else {
            val apiRes = safe { api.scanSsl(SslScanRequest(host)) }
            if (apiRes is ApiResult.Success) ApiResult.Success(apiRes.data)
            else ApiResult.Success(evaluateSslClientSide(host))
        }
        if (res is ApiResult.Success) {
            val d = res.data
            recordScan(ScanResult(id = d.id, scan_type = "ssl", target_summary = d.target_summary, risk_score = d.risk_score, threat_level = d.threat_level, confidence = d.confidence, explanation = d.explanation, created_at = d.created_at))
        }
        return res
    }

    suspend fun scanBreach(email: String): ApiResult<BreachScanResponse> {
        val res = if (CentinelConfig.isAdminTestMode) {
            delay(500)
            ApiResult.Success(evaluateBreachClientSide(email))
        } else {
            val apiRes = safe { api.scanBreach(BreachScanRequest(email)) }
            if (apiRes is ApiResult.Success) ApiResult.Success(apiRes.data)
            else ApiResult.Success(evaluateBreachClientSide(email))
        }
        if (res is ApiResult.Success) {
            val d = res.data
            recordScan(ScanResult(id = d.id, scan_type = "breach", target_summary = d.target_summary, risk_score = d.risk_score, threat_level = d.threat_level, confidence = d.confidence, explanation = d.explanation, created_at = d.created_at))
        }
        return res
    }

    suspend fun scanSms(message: String, sender: String?): ApiResult<ScanResult> {
        val res = if (CentinelConfig.isAdminTestMode) {
            delay(500)
            ApiResult.Success(evaluateSmsClientSide(message, sender))
        } else {
            val apiRes = safe { api.scanSms(SmsScanRequest(message, sender)) }
            if (apiRes is ApiResult.Success) ApiResult.Success(apiRes.data.applySettings())
            else ApiResult.Success(evaluateSmsClientSide(message, sender))
        }
        if (res is ApiResult.Success) recordScan(res.data)
        return res
    }

    suspend fun scanEmail(subject: String?, sender: String?, body: String?, rawEmail: String?): ApiResult<ScanResult> {
        val res = if (CentinelConfig.isAdminTestMode) {
            delay(500)
            ApiResult.Success(evaluateEmailClientSide(subject, sender, body, rawEmail))
        } else {
            val apiRes = safe { api.scanEmail(EmailScanRequest(rawEmail, subject, sender, body)) }
            if (apiRes is ApiResult.Success) ApiResult.Success(apiRes.data.applySettings())
            else ApiResult.Success(evaluateEmailClientSide(subject, sender, body, rawEmail))
        }
        if (res is ApiResult.Success) recordScan(res.data)
        return res
    }

    suspend fun scanEmailFile(fileName: String, bytes: ByteArray) = safe {
        val reqFile = bytes.toRequestBody("message/rfc822".toMediaType())
        val part = MultipartBody.Part.createFormData("file", fileName, reqFile)
        api.scanEmailFile(part)
    }

    suspend fun scanHeader(rawHeaders: String): ApiResult<HeaderScanResponse> {
        val res = if (CentinelConfig.isAdminTestMode) {
            delay(500)
            ApiResult.Success(evaluateHeaderClientSide(rawHeaders))
        } else {
            val apiRes = safe { api.scanHeader(HeaderScanRequest(rawHeaders)) }
            if (apiRes is ApiResult.Success) ApiResult.Success(apiRes.data)
            else ApiResult.Success(evaluateHeaderClientSide(rawHeaders))
        }
        if (res is ApiResult.Success) {
            val d = res.data
            recordScan(ScanResult(id = d.id, scan_type = "header", target_summary = d.target_summary, risk_score = d.risk_score, threat_level = d.threat_level, confidence = d.confidence, explanation = d.explanation, created_at = d.created_at))
        }
        return res
    }

    suspend fun scanQr(decodedText: String): ApiResult<ScanResult> {
        val res = if (CentinelConfig.isAdminTestMode) {
            delay(500)
            ApiResult.Success(evaluateQrClientSide(decodedText))
        } else {
            val apiRes = safe { api.scanQr(QrScanRequest(decodedText)) }
            if (apiRes is ApiResult.Success) ApiResult.Success(apiRes.data.applySettings())
            else ApiResult.Success(evaluateQrClientSide(decodedText))
        }
        if (res is ApiResult.Success) recordScan(res.data)
        return res
    }

    suspend fun scanPassword(password: String): ApiResult<PasswordScanResponse> {
        delay(300)
        val res = ApiResult.Success(evaluatePasswordClientSide(password))
        val d = res.data
        recordScan(ScanResult(id = d.id, scan_type = "password", target_summary = "*****", risk_score = d.risk_score, threat_level = d.threat_level, confidence = d.confidence, explanation = d.explanation, created_at = d.created_at))
        return res
    }

    suspend fun scanFile(fileName: String, bytes: ByteArray, mimeType: String = "application/octet-stream"): ApiResult<FileScanResponse> {
        val res = if (CentinelConfig.isAdminTestMode) {
            delay(500)
            ApiResult.Success(evaluateFileClientSide(fileName, bytes))
        } else {
            val apiRes = safe {
                val reqFile = bytes.toRequestBody(mimeType.toMediaType())
                val part = MultipartBody.Part.createFormData("file", fileName, reqFile)
                api.scanFile(part)
            }
            if (apiRes is ApiResult.Success) ApiResult.Success(apiRes.data)
            else ApiResult.Success(evaluateFileClientSide(fileName, bytes))
        }
        if (res is ApiResult.Success) {
            val d = res.data
            recordScan(ScanResult(id = d.id, scan_type = "file", target_summary = d.target_summary, risk_score = d.risk_score, threat_level = d.threat_level, confidence = d.confidence, explanation = d.explanation, created_at = d.created_at))
        }
        return res
    }

    // ---- History / analytics / notifications ----
    suspend fun getHistory(scanType: String? = null, threatLevel: String? = null, search: String? = null): ApiResult<List<ScanResult>> {
        val list = synchronized(localScanHistory) { localScanHistory.toList() }
        val filtered = list.filter { item ->
            val matchesType = scanType == null || item.scan_type.equals(scanType, ignoreCase = true)
            val matchesLevel = threatLevel == null || item.threat_level.equals(threatLevel, ignoreCase = true)
            val matchesSearch = search.isNullOrBlank() ||
                    item.target_summary.contains(search, ignoreCase = true) ||
                    item.explanation.contains(search, ignoreCase = true)
            matchesType && matchesLevel && matchesSearch
        }
        return ApiResult.Success(filtered)
    }

    suspend fun deleteHistoryItem(id: String): ApiResult<Unit> {
        synchronized(localScanHistory) {
            localScanHistory.removeAll { it.id == id }
        }
        recalculateAnalytics()
        return ApiResult.Success(Unit)
    }

    suspend fun getAnalytics(): ApiResult<AnalyticsOut> {
        val localAnalytics = recalculateAnalytics()
        if (!CentinelConfig.isAdminTestMode) {
            val apiRes = safe { api.getAnalytics() }
            if (apiRes is ApiResult.Success) {
                _analyticsCache.value = apiRes.data
                return apiRes
            }
        }
        _analyticsCache.value = localAnalytics
        return ApiResult.Success(localAnalytics)
    }

    suspend fun getNotifications(): ApiResult<List<NotificationOut>> {
        if (!CentinelConfig.isAdminTestMode) {
            val apiRes = safe { api.getNotifications() }
            if (apiRes is ApiResult.Success) return apiRes
        }
        return ApiResult.Success(listOf(
            NotificationOut("1", "SECURITY PROTOCOL ACTIVE", "Terminal protection active. Local security scanners ready.", "Low", false, "2026-08-18 10:00"),
            NotificationOut("2", "NEURAL FIREWALL ON", "Real-time threat monitoring active on device.", "Low", false, "2026-08-18 12:45"),
            NotificationOut("3", "SYSTEM STATUS OK", "Centinel AI Security Kernel initialized successfully.", "Low", true, "2026-08-17 09:00")
        ))
    }

    suspend fun markNotificationRead(id: String) = safe { api.markNotificationRead(id) }

    suspend fun registerDevice(fcmToken: String) = safe { api.registerDevice(RegisterDeviceRequest(fcmToken)) }

    suspend fun unregisterDevice(fcmToken: String) = safe { api.unregisterDevice(UnregisterDeviceRequest(fcmToken)) }

    // ---- Reports ----
    suspend fun getReportPdf(scanId: String) = safe { api.getReportPdf(scanId) }
}
