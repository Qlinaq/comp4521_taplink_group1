package com.taplink.app.platform

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.taplink.app.domain.AppPreferences
import java.util.concurrent.TimeUnit

object PlatformWorkScheduler {
    const val REMINDERS_WORK = "taplink.reminders.v1"
    const val CONTENT_WORK = "taplink.demo-content.v1"
    const val WIDGET_WORK = "taplink.widget-refresh.v1"

    /** Unique persistent work; UPDATE preserves an existing period instead of restarting its clock. */
    fun synchronize(context: Context, prefs: AppPreferences) {
        val work = WorkManager.getInstance(context.applicationContext)
        if (ReminderPolicy.workEnabled(prefs)) {
            work.enqueueUniquePeriodicWork(
                REMINDERS_WORK, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<TapLinkReminderWorker>(6, TimeUnit.HOURS)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build(),
            )
        } else work.cancelUniqueWork(REMINDERS_WORK)
        if (prefs.demoRefreshEnabled) {
            work.enqueueUniquePeriodicWork(
                CONTENT_WORK, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<TapLinkContentRefreshWorker>(24, TimeUnit.HOURS)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build(),
            )
        } else work.cancelUniqueWork(CONTENT_WORK)
    }

    /** APPEND_OR_REPLACE keeps a final refresh when data changes during an already-running refresh. */
    fun refreshWidget(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            WIDGET_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<TapLinkWidgetRefreshWorker>().build(),
        )
    }

    fun checkReminders(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "taplink.reminders.check.v1", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<TapLinkReminderWorker>().build(),
        )
    }
}
