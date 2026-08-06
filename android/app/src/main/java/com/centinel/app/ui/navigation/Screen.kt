package com.centinel.app.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Dashboard : Screen("dashboard")
    object UrlScanner : Screen("scan_url")
    object WebsiteScanner : Screen("scan_website")
    object EmailScanner : Screen("scan_email")
    object HeaderAnalyzer : Screen("scan_header")
    object SmsScanner : Screen("scan_sms")
    object QrScanner : Screen("scan_qr")
    object FileScanner : Screen("scan_file")
    object PasswordAnalyzer : Screen("scan_password")
    object SslChecker : Screen("scan_ssl")
    object BreachChecker : Screen("scan_breach")
    object History : Screen("history")
    object Analytics : Screen("analytics")
    object Notifications : Screen("notifications")
}
