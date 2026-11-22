package com.taplink.app.platform

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent

object TapLinkNavigation {
    const val EXTRA_DESTINATION = "taplink_destination"
    const val DASHBOARD = "dashboard"
    const val MESSAGES = "messages"

    /** An explicit immutable intent, never a payment or other wallet mutation. */
    internal fun pendingIntent(context: Context, destination: String): PendingIntent {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(context.packageName, "com.taplink.app.MainActivity")
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_DESTINATION, destination)
        }
        return PendingIntent.getActivity(
            context, if (destination == MESSAGES) 2 else 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
