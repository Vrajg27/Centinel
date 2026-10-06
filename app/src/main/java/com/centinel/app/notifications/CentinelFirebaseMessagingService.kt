package com.centinel.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.centinel.app.R
import com.centinel.app.data.local.SettingsStore
import com.centinel.app.data.repository.CentinelRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

const val CENTINEL_NOTIFICATION_CHANNEL_ID = "centinel_threat_alerts"
private const val TAG = "CentinelFCM"

class CentinelFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val settingsStore = SettingsStore(applicationContext)

        val title = message.notification?.title ?: "Centinel Alert"
        val body = message.notification?.body ?: "A new security event was detected."

        val isHighOrCritical = title.contains("High", ignoreCase = true) ||
                title.contains("Critical", ignoreCase = true) ||
                body.contains("High", ignoreCase = true) ||
                body.contains("Critical", ignoreCase = true)

        if (isHighOrCritical && !settingsStore.notifyHighRisk) {
            Log.d(TAG, "High/Critical threat notifications suppressed by user settings")
            return
        }

        showNotification(title, body, settingsStore.notifySoundVibration)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                CentinelRepository(applicationContext).registerDevice(token)
            } catch (e: Exception) {
                Log.w(TAG, "Could not register new FCM token", e)
            }
        }
    }

    private fun showNotification(title: String, body: String, enableSoundAndVibration: Boolean) {
        ensureNotificationChannel(enableSoundAndVibration)

        val hasPermission = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted — showing in-app Notifications screen only")
            return
        }

        val builder = NotificationCompat.Builder(this, CENTINEL_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (enableSoundAndVibration) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        if (!enableSoundAndVibration) {
            builder.setDefaults(0)
            builder.setVibrate(longArrayOf(0L))
        }

        NotificationManagerCompat.from(this).notify(Random.nextInt(), builder.build())
    }

    private fun ensureNotificationChannel(enableSoundAndVibration: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val importance = if (enableSoundAndVibration) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(
            CENTINEL_NOTIFICATION_CHANNEL_ID,
            "Threat Alerts",
            importance,
        ).apply {
            description = "High and Critical risk scan results from Centinel"
            if (!enableSoundAndVibration) {
                setSound(null, null)
                enableVibration(false)
            }
        }
        manager.createNotificationChannel(channel)
    }
}
