package com.taplink.app.ui

import androidx.compose.runtime.State
import com.taplink.app.domain.AppPreferences
import com.taplink.app.domain.Receipt
import com.taplink.app.domain.ServiceLocation

/** Device integration boundary. Null capabilities are omitted from the UI, never simulated. */
data class PlatformActions(
    val requestLocation: (((Double?, Double?, String?) -> Unit) -> Unit)? = null,
    val cancelLocation: (() -> Unit)? = null,
    val openMap: ((ServiceLocation) -> Unit)? = null,
    val shareReceipt: ((Receipt) -> Unit)? = null,
    val requestNotifications: (() -> Unit)? = null,
    val pinWidget: (() -> Unit)? = null,
    val scanMerchant: (((String, Long?) -> Unit) -> Unit)? = null,
    val dashboardVisibility: ((Boolean, AppPreferences, () -> Unit) -> Unit)? = null,
    val preferencesChanged: ((AppPreferences) -> Unit)? = null,
    /** Observable so the settings screen reflects a permission granted outside the app. */
    val notificationsAllowed: State<Boolean>? = null,
    /** Re-reads the permission when the screen opens, not only when the activity resumes. */
    val refreshNotificationState: (() -> Unit)? = null,
    val openNotificationSettings: (() -> Unit)? = null,
    val sendTestNotification: (() -> Unit)? = null,
    val openAccessibilitySettings: (() -> Unit)? = null,
    val openKeyboardSettings: (() -> Unit)? = null,
    val openUrl: ((String) -> Unit)? = null,
    val openWhatsApp: ((String) -> Unit)? = null,
    val dial: ((String) -> Unit)? = null,
    val speak: ((String) -> Unit)? = null,
    val stopSpeaking: (() -> Unit)? = null,
)
