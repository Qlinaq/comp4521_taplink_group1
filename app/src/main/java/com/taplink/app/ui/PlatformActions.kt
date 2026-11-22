package com.taplink.app.ui

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
)
