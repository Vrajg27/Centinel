package com.centinel.app.data.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val TAG = "CentinelPush"

/**
 * Suspend wrapper around FirebaseMessaging's Task-based token API. Uses
 * suspendCancellableCoroutine directly rather than pulling in the
 * kotlinx-coroutines-play-services dependency just for its .await()
 * extension — one small function, no extra dependency to declare.
 *
 * Returns null (rather than throwing) if the token can't be fetched —
 * e.g. Firebase isn't actually configured yet (no google-services.json),
 * no network, etc. Every caller treats null as "couldn't get a token right
 * now, skip registration" rather than a hard error.
 */
suspend fun getCurrentFcmToken(): String? = suspendCancellableCoroutine { cont ->
    try {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> if (cont.isActive) cont.resume(token) }
            .addOnFailureListener { e ->
                Log.w(TAG, "Could not fetch FCM token", e)
                if (cont.isActive) cont.resume(null)
            }
    } catch (e: IllegalStateException) {
        // Thrown if FirebaseApp hasn't been initialized (e.g. no
        // google-services.json / plugin not applied yet) — see
        // notifications/CentinelFirebaseMessagingService.kt and
        // android/README.md for the setup steps this depends on.
        Log.w(TAG, "Firebase isn't initialized — push notifications are inactive until it is", e)
        if (cont.isActive) cont.resume(null)
    }
}
