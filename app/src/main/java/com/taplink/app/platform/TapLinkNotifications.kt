package com.taplink.app.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.taplink.app.R
import com.taplink.app.domain.AppLanguage
import com.taplink.app.domain.AppPreferences
import com.taplink.app.domain.WalletMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object TapLinkNotifications {
    const val CHANNEL_ID = "taplink_reminders_v1"
    private const val TAG_PREFIX = "taplink_message:"
    private const val TEST_TAG = "taplink_test"
    private val deliveryLock = Mutex()

    /** UI requests this permission after the user opts in. Workers never launch permission UI. */
    fun permissionToRequest(): String? =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null

    /**
     * Notifications are built outside Compose, so the locale has to be applied here. Without this,
     * a Chinese interface would post English alerts.
     */
    fun localizedContext(context: Context, language: AppLanguage): Context {
        val configuration = Configuration(context.resources.configuration).apply { setLocale(language.locale) }
        return context.createConfigurationContext(configuration)
    }

    /** Re-creating an existing channel updates its name, so a language change reaches the system UI. */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.notif_channel_description)
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
                val localized = localizedContext(app, prefs.language)
                createChannel(localized)
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
                    val title = MessageCopy.title(localized, message)
                    val body = MessageCopy.body(localized, message)
                    val publicVersion = NotificationCompat.Builder(app, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_taplink)
                        .setContentTitle(localized.getString(R.string.notif_public_title))
                        .setContentText(localized.getString(R.string.notif_public_body)).build()
                    val notification = NotificationCompat.Builder(app, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_taplink)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                        .setSubText(localized.getString(R.string.notif_subtext))
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

    /**
     * A test notification proves the channel and permission actually work on this device. It is
     * local and simulated; nothing leaves the phone.
     */
    @SuppressLint("MissingPermission")
    suspend fun postTest(context: Context, prefs: AppPreferences): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val localized = localizedContext(app, prefs.language)
        createChannel(localized)
        if (!canPost(app)) return@withContext false
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_taplink)
            .setContentTitle(localized.getString(R.string.notif_test_title))
            .setContentText(localized.getString(R.string.notif_test_body))
            .setStyle(NotificationCompat.BigTextStyle().bigText(localized.getString(R.string.notif_test_body)))
            .setSubText(localized.getString(R.string.notif_subtext))
            .setContentIntent(TapLinkNavigation.pendingIntent(app, TapLinkNavigation.MESSAGES))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .build()
        runCatching { app.getSystemService(NotificationManager::class.java).notify(TEST_TAG, 0, notification) }
            .isSuccess
    }
}
