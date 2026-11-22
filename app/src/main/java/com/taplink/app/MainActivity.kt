package com.taplink.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.taplink.app.data.TapLinkContainer
import com.taplink.app.domain.AppPreferences
import com.taplink.app.platform.*
import com.taplink.app.ui.PlatformActions
import com.taplink.app.ui.TapLinkApp
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

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { locate() }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationAsked = true
        TapLinkPlatform.onPermissionsChanged(this)
        toast(if (granted) "Notifications enabled. Your messages are also saved in TapLink." else "Notifications are off. In-app messages remain available.")
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
            openMap = { point -> if (!ExternalIntents.openMap(this, point)) toast("No map app is available. The address is shown in TapLink.") },
            shareReceipt = { receipt -> if (!ExternalIntents.shareReceipt(this, receipt)) toast("No compatible sharing app is available.") },
            requestNotifications = {
                val permission = TapLinkNotifications.permissionToRequest()
                if (permission != null && !TapLinkNotifications.canPost(this) && !notificationAsked) notificationPermission.launch(permission)
                else runCatching { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) }.onFailure { toast("Open Android Settings → Apps → TapLink → Notifications.") }
            },
            pinWidget = { if (!TapLinkWidgetProvider.requestPin(this)) toast("Long-press your home screen, open Widgets, and choose TapLink.") },
            dashboardVisibility = { visible, prefs, toggle ->
                preferences = prefs
                toggleBalance = toggle
                shake.setEnabled(visible && prefs.shakeToToggleEnabled)
            },
            preferencesChanged = { preferences = it },
        )
        setContent { TapLinkApp(TapLinkContainer.repository(applicationContext), actions, destination) }
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

    override fun onResume() { super.onResume(); TapLinkPlatform.onPermissionsChanged(this) }
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
    override fun onDestroy() { locationJob?.cancel(); locationCallback = null; if (::shake.isInitialized) shake.close(); super.onDestroy() }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
}
