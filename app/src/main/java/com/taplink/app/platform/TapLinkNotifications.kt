package com.taplink.app.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.taplink.app.R
import com.taplink.app.domain.AppPreferences
import com.taplink.app.domain.WalletMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object TapLinkNotifications {
    const val CHANNEL_ID = "taplink_reminders_v1"
    private const val TAG_PREFIX = "taplink_message:"
    private val deliveryLock = Mutex()

    /** UI requests this permission after the user opts in. Workers never launch permission UI. */
    fun permissionToRequest(): String? =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "TapLink demo reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Simulated card balance, pass and offer reminders"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            setShowBadge(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean {
        if (permissionToRequest()?.let { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED } == true) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL_ID)
            ?.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** Persistent message-ID dedup, shared by foreground flow collection and the background worker. */
    @SuppressLint("MissingPermission") // canPost checks runtime permission; notify also catches revocation.
    suspend fun publishPending(context: Context, messages: List<WalletMessage>, prefs: AppPreferences): Int =
        withContext(Dispatchers.IO) {
            deliveryLock.withLock {
                val app = context.applicationContext
                createChannel(app)
                val now = System.currentTimeMillis()
                val manager = app.getSystemService(NotificationManager::class.java)
                val allowedIds = messages.filter { ReminderPolicy.allowed(it, prefs, now) }.map { it.id }.toSet()
                // Reading a message or disabling its preference also removes an already shown alert.
                manager.activeNotifications.filter { it.tag?.startsWith(TAG_PREFIX) == true }
                    .filter { it.tag?.removePrefix(TAG_PREFIX) !in allowedIds }
                    .forEach { manager.cancel(it.tag, it.id) }
                if (!canPost(app)) return@withLock 0
                val storage = app.getSharedPreferences("taplink_notification_delivery", Context.MODE_PRIVATE)
                val delivered = storage.getStringSet("message_ids", emptySet()).orEmpty().toMutableSet()
                var count = 0
                for (message in ReminderPolicy.pending(messages, prefs, delivered, now)) {
                    val publicVersion = NotificationCompat.Builder(app, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_taplink)
                        .setContentTitle("TapLink demo reminder")
                        .setContentText("Open TapLink to view your message.").build()
                    val notification = NotificationCompat.Builder(app, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_taplink)
                        .setContentTitle(message.title)
                        .setContentText(message.body)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
                        .setSubText("Simulated wallet · No real money")
                        .setContentIntent(TapLinkNavigation.pendingIntent(app, TapLinkNavigation.MESSAGES))
                        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                        .setPublicVersion(publicVersion)
                        .setCategory(NotificationCompat.CATEGORY_REMINDER)
                        .setOnlyAlertOnce(true)
                        .setAutoCancel(true)
                        .setWhen(message.createdAt)
                        .build()
                    try {
                        // A stable tag avoids collisions and replaces an existing alert after a crash.
                        manager.notify(TAG_PREFIX + message.id, 0, notification)
                    } catch (_: SecurityException) {
                        break // Permission may have been revoked after the preflight check.
                    }
                    delivered += message.id
                    // Commit before releasing the mutex; do not mark denied notifications as delivered.
                    if (!storage.edit().putStringSet("message_ids", delivered.toSet()).commit()) break
                    count++
                }
                count
            }
        }
}
