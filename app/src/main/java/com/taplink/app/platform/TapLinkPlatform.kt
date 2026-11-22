package com.taplink.app.platform

import android.content.Context
import android.util.Log
import com.taplink.app.data.TapLinkContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

object TapLinkPlatform {
    private var started = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Call from Application.onCreate or MainActivity.onCreate. Retains only application context. */
    @Synchronized
    fun start(context: Context) {
        if (started) return
        val app = context.applicationContext
        val repository = TapLinkContainer.repository(app)
        TapLinkNotifications.createChannel(app)
        started = true
        scope.launch {
            combine(repository.cards, repository.preferences) { cards, prefs ->
                cards to (prefs.selectedCardId to prefs.widgetBalanceVisible)
            }.distinctUntilChanged().onEach { TapLinkWidgetProvider.refresh(app) }
                .retryWhen { error, _ -> Log.w("TapLinkPlatform", "Widget refresh will retry", error); delay(5_000); true }
                .collect {}
        }
        scope.launch {
            repository.preferences.distinctUntilChanged { previous, next ->
                previous.remindersEnabled == next.remindersEnabled &&
                    previous.lowBalanceAlerts == next.lowBalanceAlerts &&
                    previous.expiryRemindersEnabled == next.expiryRemindersEnabled &&
                    previous.demoRefreshEnabled == next.demoRefreshEnabled
            }.onEach {
                PlatformWorkScheduler.synchronize(app, it)
            }.retryWhen { error, _ -> Log.w("TapLinkPlatform", "Scheduling will retry", error); delay(5_000); true }
                .collect {}
        }
        scope.launch {
            combine(repository.messages, repository.preferences) { messages, prefs -> messages to prefs }
                .distinctUntilChanged().onEach { (messages, prefs) -> TapLinkNotifications.publishPending(app, messages, prefs) }
                .retryWhen { error, _ -> Log.w("TapLinkPlatform", "Notifications will retry", error); delay(5_000); true }
                .collect {}
        }
    }

    /** Call after granting notifications, or on resume to notice changes made in system settings. */
    fun onPermissionsChanged(context: Context) {
        start(context)
        PlatformWorkScheduler.checkReminders(context)
    }
}
