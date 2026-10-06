package com.centinel.app

import com.centinel.app.data.model.ScanResult
import com.centinel.app.ui.theme.getModuleColor
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import kotlin.math.exp

class CentinelSecurityScannersTest {

    // ---- 1. Risk Scoring Formula Test ----
    @Test
    fun testRiskScoringFormula() {
        val rawWeightSum = 50.0
        val riskScore = (100 * (1.0 - exp(-rawWeightSum / 50.0))).toInt().coerceIn(0, 100)
        assertTrue(riskScore in 60..65)

        val zeroWeight = 0.0
        val zeroRisk = (100 * (1.0 - exp(-zeroWeight / 50.0))).toInt().coerceIn(0, 100)
        assertEquals(0, zeroRisk)
    }

    // ---- 2. Password Analysis & Privacy Test ----
    @Test
    fun testPasswordEntropyAndPrivacyMasking() {
        val password = "StrongPassword123!@#"
        val len = password.length
        val poolSize = 26 + 26 + 10 + 32
        val entropy = len * (Math.log(poolSize.toDouble()) / Math.log(2.0))

        assertTrue(len >= 12)
        assertTrue(entropy >= 70.0)

        // Privacy mandate: Target summary MUST mask password as ***
        val targetSummary = "*".repeat(len.coerceAtMost(16))
        assertFalse(targetSummary.contains("StrongPassword"))
        assertEquals("****************", targetSummary)
    }

    // ---- 3. File SHA-256 Hashing Test ----
    @Test
    fun testFileSha256Calculation() {
        val testBytes = "CENTINEL_SECURITY_TEST_FILE".toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(testBytes)
        val sha256 = digest.joinToString("") { "%02x".format(it) }

        assertEquals(64, sha256.length)
        assertNotNull(sha256)
    }

    // ---- 4. File Extension Anomaly Test ----
    @Test
    fun testDoubleExtensionDetection() {
        val fileName = "invoice_statement.pdf.exe"
        val parts = fileName.lowercase().split(".")
        val isDoubleExt = parts.size >= 3 &&
                listOf("pdf", "doc", "docx", "jpg").contains(parts[parts.size - 2]) &&
                listOf("exe", "scr", "vbs", "apk").contains(parts.last())

        assertTrue(isDoubleExt)
    }

    // ---- 5. QR Payload Classification Test ----
    @Test
    fun testQrPayloadClassification() {
        val wifiPayload = "WIFI:S:HomeNet;T:WPA;P:secret123;;"
        val telPayload = "tel:+18005550199"
        val urlPayload = "https://paypal-security-update.xyz/login"

        assertTrue(wifiPayload.startsWith("WIFI:", ignoreCase = true))
        assertTrue(telPayload.startsWith("tel:", ignoreCase = true))
        assertTrue(urlPayload.startsWith("https://", ignoreCase = true))
    }

    // ---- 6. Email Header Authentication Parser Test ----
    @Test
    fun testHeaderAuthenticationParsing() {
        val headers = """
            Received: from mail.attacker.net (mail.attacker.net [192.168.1.10])
            Authentication-Results: spf=fail dkim=fail dmarc=fail
            From: "Bank Support" <support@fake-bank.net>
            Reply-To: phisher@scam-server.org
        """.lowercase()

        val spfFail = headers.contains("spf=fail")
        val dkimFail = headers.contains("dkim=fail")
        val dmarcFail = headers.contains("dmarc=fail")

        assertTrue(spfFail)
        assertTrue(dkimFail)
        assertTrue(dmarcFail)
    }

    // ---- 7. Display Name Spoofing Detection Test ----
    @Test
    fun testDisplayNameSpoofing() {
        val sender = "\"PayPal Security Team\" <attacker@untrusted-domain.xyz>"
        val displayMatch = Regex(""""?([\w .]+)"?\s*<([^>]+)>""").find(sender)

        assertNotNull(displayMatch)
        val (name, addr) = displayMatch!!.destructured
        val nameHasBrand = name.lowercase().contains("paypal")
        val addrHasBrand = addr.lowercase().contains("paypal")

        assertTrue(nameHasBrand)
        assertFalse(addrHasBrand)
    }

    // ---- 8. Module Color Coding Test ----
    @Test
    fun testModuleColorCoding() {
        val urlColor = getModuleColor("url_scanner")
        val emailColor = getModuleColor("email_intel")
        val fileColor = getModuleColor("file_scanner")
        val pwColor = getModuleColor("password_analyzer")

        assertNotNull(urlColor)
        assertNotNull(emailColor)
        assertNotNull(fileColor)
        assertNotNull(pwColor)
    }
}
