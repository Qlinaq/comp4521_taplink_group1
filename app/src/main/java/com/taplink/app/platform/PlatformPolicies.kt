package com.taplink.app.platform

import com.taplink.app.domain.AppPreferences
import com.taplink.app.domain.Card
import com.taplink.app.domain.MessageType
import com.taplink.app.domain.Money
import com.taplink.app.domain.WalletMessage

internal data class WidgetContent(val name: String, val identifier: String, val balance: String, val status: String) {
    companion object {
        fun from(card: Card?, preferences: AppPreferences): WidgetContent = WidgetContent(
            name = card?.name ?: "Your TapLink card",
            // Defensive masking: a widget never displays more than four identifier characters.
            identifier = card?.maskedNumber?.filter(Char::isLetterOrDigit)?.takeLast(4)
                ?.takeIf(String::isNotEmpty)?.let { "•••• $it" } ?: "No card selected",
            balance = when {
                card == null -> "Open TapLink to get started"
                preferences.widgetBalanceVisible -> Money.format(card.balanceCents)
                else -> "Balance hidden"
            },
            status = if (card?.isFrozen == true) "Frozen · Demo card" else "Simulated HKD · Demo card",
        )
    }
}

/**
 * Which in-app messages also become an OS notification.
 *
 * Two different switches exist on purpose: [AppPreferences.notificationsEnabled] is the master push
 * switch (Octopus-style "receive notifications"), while [AppPreferences.remindersEnabled] only
 * controls whether the background reminder job is scheduled at all. A user may want instant
 * transaction alerts but no scheduled job, so the two are not collapsed into one flag.
 */
internal object ReminderPolicy {
    const val MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L

    fun categoryEnabled(type: MessageType, prefs: AppPreferences): Boolean = when (type) {
        MessageType.LOW_BALANCE -> prefs.lowBalanceAlerts
        MessageType.TRANSACTION -> prefs.transactionAlerts
        MessageType.PASS_EXPIRY -> prefs.expiryRemindersEnabled
        MessageType.OFFER_EXPIRY -> prefs.offerAlerts
        MessageType.SERVICE -> prefs.serviceAnnouncements
        MessageType.INFO -> false
    }

    fun workEnabled(prefs: AppPreferences): Boolean = prefs.remindersEnabled && prefs.notificationsEnabled &&
        (prefs.lowBalanceAlerts || prefs.expiryRemindersEnabled || prefs.transactionAlerts || prefs.offerAlerts)

    fun allowed(message: WalletMessage, prefs: AppPreferences, now: Long): Boolean =
        prefs.notificationsEnabled && !message.isRead && message.createdAt <= now &&
            message.createdAt >= now - MAX_AGE_MILLIS && categoryEnabled(message.type, prefs)

    fun pending(messages: List<WalletMessage>, prefs: AppPreferences, delivered: Set<String>, now: Long): List<WalletMessage> =
        messages.asSequence().filter { allowed(it, prefs, now) && it.id !in delivered }
            .distinctBy { it.id }.sortedByDescending { it.createdAt }.take(5).toList()
}
