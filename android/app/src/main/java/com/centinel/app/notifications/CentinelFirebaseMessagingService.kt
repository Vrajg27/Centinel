package com.centinel.app.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives push notifications for events like "new phishing detected",
 * "browser extension blocked website", "data breach found", etc.
 *
 * This is a stub: wiring it up fully requires (1) adding
 * app/google-services.json from a Firebase project, (2) uncommenting the
 * google-services plugin in app/build.gradle.kts, and (3) sending the
 * device's FCM token to the backend (e.g. a new POST /notifications/register-device
 * endpoint) so the server can target this device.
 */
class CentinelFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: "Centinel Alert"
        val body = message.notification?.body ?: "A new security event was detected."
        // TODO: show a local notification using NotificationManagerCompat here.
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // TODO: send `token` to the backend so it can push notifications to this device.
    }
}
