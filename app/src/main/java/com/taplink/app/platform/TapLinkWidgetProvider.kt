package com.taplink.app.platform

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.taplink.app.R
import com.taplink.app.data.TapLinkContainer
import com.taplink.app.domain.AppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TapLinkWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Safe immediate content; database access is delegated to durable work.
        render(context, manager, ids, WidgetContent.from(null, AppPreferences()))
        PlatformWorkScheduler.refreshWidget(context)
    }

    override fun onEnabled(context: Context) {
        PlatformWorkScheduler.refreshWidget(context)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        onUpdate(context, AppWidgetManager.getInstance(context), newWidgetIds)
    }

    companion object {
        private val refreshLock = Mutex()

        /** Shows the launcher's own confirmation UI. False means manual launcher placement is needed. */
        fun requestPin(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) return false
            return try {
                manager.requestPinAppWidget(ComponentName(context, TapLinkWidgetProvider::class.java), null, null)
            } catch (_: IllegalStateException) { false }
            catch (_: SecurityException) { false }
        }

        /** Refresh all instances after a mutation; the coordinator also does this automatically. */
        suspend fun refresh(context: Context) = refreshLock.withLock {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, TapLinkWidgetProvider::class.java))
            if (ids.isEmpty()) return@withLock
            val repository = TapLinkContainer.repository(app)
            repository.initialize()
            val cards = repository.cards.first()
            val prefs = repository.preferences.first()
            val selected = cards.find { it.id == prefs.selectedCardId }
            render(app, manager, ids, WidgetContent.from(selected, prefs))
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray, content: WidgetContent) {
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, remoteViews(context, content))
        }

        internal fun remoteViews(context: Context, content: WidgetContent): RemoteViews =
            RemoteViews(context.packageName, R.layout.taplink_widget).apply {
                setTextViewText(R.id.widget_card_name, content.name)
                setTextViewText(R.id.widget_card_number, content.identifier)
                setTextViewText(R.id.widget_balance, content.balance)
                setTextViewText(R.id.widget_status, content.status)
                setOnClickPendingIntent(R.id.widget_root, TapLinkNavigation.pendingIntent(context, TapLinkNavigation.DASHBOARD))
                setOnClickPendingIntent(R.id.widget_open, TapLinkNavigation.pendingIntent(context, TapLinkNavigation.DASHBOARD))
            }
    }
}
