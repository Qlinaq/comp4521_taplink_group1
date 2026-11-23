package com.taplink.app.platform

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.taplink.app.data.TapLinkContainer
import com.taplink.app.domain.WalletResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class TapLinkWidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = guarded {
        TapLinkWidgetProvider.refresh(applicationContext)
        Result.success()
    }
}

class TapLinkReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = guarded {
        val repository = TapLinkContainer.repository(applicationContext)
        repository.useOwner(TapLinkContainer.activeOwner(applicationContext))
        repository.initialize()
        val prefs = repository.preferences.first()
        if (ReminderPolicy.workEnabled(prefs)) {
            if (repository.generateReminders() is WalletResult.Error) return@guarded retryOrFail()
        }
        // Re-read after generation: toggles made while work was running must be respected.
        TapLinkNotifications.publishPending(applicationContext, repository.messages.first(), repository.preferences.first())
        TapLinkWidgetProvider.refresh(applicationContext)
        Result.success()
    }
}

class TapLinkContentRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = guarded {
        val repository = TapLinkContainer.repository(applicationContext)
        repository.useOwner(TapLinkContainer.activeOwner(applicationContext))
        repository.initialize()
        if (!repository.preferences.first().demoRefreshEnabled) return@guarded Result.success()
        if (repository.refreshDemoContent() is WalletResult.Error) return@guarded retryOrFail()
        TapLinkWidgetProvider.refresh(applicationContext)
        Result.success()
    }
}

private suspend fun CoroutineWorker.guarded(block: suspend () -> androidx.work.ListenableWorker.Result): androidx.work.ListenableWorker.Result =
    try { block() } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) {
        Log.w("TapLinkWork", "${javaClass.simpleName} failed; attempt $runAttemptCount", error)
        retryOrFail()
    }

private fun CoroutineWorker.retryOrFail(): androidx.work.ListenableWorker.Result =
    if (runAttemptCount < 3) androidx.work.ListenableWorker.Result.retry() else androidx.work.ListenableWorker.Result.failure()
