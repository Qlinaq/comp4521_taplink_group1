package com.taplink.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.taplink.app.data.TapLinkContainer
import com.taplink.app.domain.AppPreferences
import com.taplink.app.domain.SupportContacts
import com.taplink.app.platform.*
import com.taplink.app.ui.PlatformActions
import com.taplink.app.ui.TapLinkRoot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var destination by mutableStateOf<String?>(null)
    private var preferences = AppPreferences()
    private var toggleBalance: () -> Unit = {}
    private lateinit var shake: LifecycleShakeObserver
    private var locationJob: Job? = null
    private var locationCallback: ((Double?, Double?, String?) -> Unit)? = null
    private var notificationAsked = false
    private val notificationsAllowed = mutableStateOf(false)
    private var voice: VoiceReader? = null

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { locate() }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationAsked = true
        notificationsAllowed.value = TapLinkNotifications.canPost(this)
        TapLinkPlatform.onPermissionsChanged(this)
        toast(if (granted) R.string.toast_notifications_on else R.string.toast_notifications_off)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        TapLinkPlatform.start(this)
        notificationAsked = savedInstanceState?.getBoolean("notificationAsked") ?: false
        if (savedInstanceState == null) readDestination(intent)
        shake = LifecycleShakeObserver(this, this) { toggleBalance() }
        val actions = PlatformActions(
            requestLocation = { callback ->
                locationJob?.cancel()
                locationCallback = callback
                if (CoarseLocationHelper.hasPermission(this)) locate() else locationPermission.launch(CoarseLocationHelper.PERMISSION)
            },
            cancelLocation = { locationJob?.cancel(); locationCallback = null },
            openMap = { point -> if (!ExternalIntents.openMap(this, point)) toast(R.string.toast_no_map_app) },
            shareReceipt = { receipt -> if (!ExternalIntents.shareReceipt(this, receipt)) toast(R.string.toast_no_share_app) },
            requestNotifications = {
                val permission = TapLinkNotifications.permissionToRequest()
                if (permission != null && !TapLinkNotifications.canPost(this) && !notificationAsked) notificationPermission.launch(permission)
                else runCatching { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) }.onFailure { toast(R.string.toast_open_settings) }
            },
            pinWidget = { if (!TapLinkWidgetProvider.requestPin(this)) toast(R.string.toast_pin_widget) },
            dashboardVisibility = { visible, prefs, toggle ->
                preferences = prefs
                toggleBalance = toggle
                shake.setEnabled(visible && prefs.shakeToToggleEnabled)
            },
            preferencesChanged = { preferences = it },
            notificationsAllowed = notificationsAllowed,
            refreshNotificationState = { notificationsAllowed.value = TapLinkNotifications.canPost(this) },
            openNotificationSettings = {
                runCatching {
                    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                }.onFailure { toast(R.string.toast_open_settings) }
            },
            sendTestNotification = {
                lifecycleScope.launch {
                    val ok = TapLinkNotifications.postTest(this@MainActivity, preferences)
                    notificationsAllowed.value = TapLinkNotifications.canPost(this@MainActivity)
                    toast(if (ok) R.string.toast_notification_sent else R.string.toast_notification_failed)
                }
            },
            openAccessibilitySettings = {
                runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    .onFailure { toast(R.string.toast_open_settings) }
            },
            openKeyboardSettings = {
                runCatching { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
                    .onFailure { toast(R.string.toast_open_settings) }
            },
            openUrl = { url -> if (!ExternalIntents.openUrl(this, url)) toast(R.string.toast_no_browser) },
            openWhatsApp = { message ->
                if (!ExternalIntents.openWhatsApp(this, SupportContacts.WHATSAPP_E164, message)) toast(R.string.toast_no_whatsapp)
            },
            dial = { number -> if (!ExternalIntents.dial(this, number)) toast(R.string.toast_no_dialer) },
            speak = { text ->
                val reader = voice ?: VoiceReader(this).also { voice = it }
                reader.speak(text, preferences.language)
                if (reader.lastError) toast(R.string.toast_voice_unavailable)
            },
            stopSpeaking = { voice?.stop() },
        )
        setContent {
            TapLinkRoot(
                repository = TapLinkContainer.repository(applicationContext),
                authRepository = TapLinkContainer.authRepository(applicationContext),
                platform = actions,
                destination = destination
            )
        }
    }

    private fun locate() {
        locationJob?.cancel()
        val callback = locationCallback ?: return
        locationJob = lifecycleScope.launch {
            try {
                when (val result = CoarseLocationHelper.locate(this@MainActivity, preferences.manualDistrict)) {
                    is LocationResult.Approximate -> callback?.invoke(result.latitude, result.longitude, "Approximate device location. Straight-line distances to demo locations are estimates.")
                    is LocationResult.Manual -> callback?.invoke(null, null, result.reason)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { callback?.invoke(null, null, "Location is unavailable. Choose a district to continue.") }
            finally { if (locationCallback === callback) locationCallback = null }
        }
    }

    override fun onResume() {
        super.onResume()
        notificationsAllowed.value = TapLinkNotifications.canPost(this)
        TapLinkPlatform.onPermissionsChanged(this)
    }
    override fun onStop() {
        if (locationJob?.isActive == true) {
            locationJob?.cancel()
            locationCallback?.invoke(null, null, "Location request paused. Choose a district or try again.")
            locationCallback = null
        }
        super.onStop()
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); readDestination(intent) }
    private fun readDestination(intent: Intent) {
        intent.getStringExtra(TapLinkNavigation.EXTRA_DESTINATION)?.let { destination = "$it|${System.nanoTime()}" }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("notificationAsked", notificationAsked); super.onSaveInstanceState(outState) }
    override fun onDestroy() {
        locationJob?.cancel()
        locationCallback = null
        voice?.close()
        voice = null
        if (::shake.isInitialized) shake.close()
        super.onDestroy()
    }
    /**
     * Toasts are outside Compose, so they resolve their own localized context from the saved
     * language preference. That keeps a Chinese screen from producing an English toast.
     */
    private fun toast(@StringRes message: Int) {
        val configuration = Configuration(resources.configuration).apply { setLocale(preferences.language.locale) }
        val localized = createConfigurationContext(configuration)
        Toast.makeText(localized, localized.getString(message), Toast.LENGTH_LONG).show()
    }
}
