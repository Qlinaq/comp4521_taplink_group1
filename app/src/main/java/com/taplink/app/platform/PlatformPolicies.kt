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

internal object ReminderPolicy {
    const val MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L

    fun workEnabled(prefs: AppPreferences): Boolean =
        prefs.remindersEnabled && (prefs.lowBalanceAlerts || prefs.expiryRemindersEnabled)

    fun allowed(message: WalletMessage, prefs: AppPreferences, now: Long): Boolean =
        prefs.remindersEnabled && !message.isRead && message.createdAt <= now &&
            message.createdAt >= now - MAX_AGE_MILLIS && when (message.type) {
                MessageType.LOW_BALANCE -> prefs.lowBalanceAlerts
                MessageType.PASS_EXPIRY, MessageType.OFFER_EXPIRY -> prefs.expiryRemindersEnabled
                MessageType.INFO -> false
            }

    fun pending(messages: List<WalletMessage>, prefs: AppPreferences, delivered: Set<String>, now: Long): List<WalletMessage> =
        messages.asSequence().filter { allowed(it, prefs, now) && it.id !in delivered }
            .distinctBy { it.id }.sortedByDescending { it.createdAt }.take(5).toList()
}
