package com.taplink.app.platform

import com.taplink.app.domain.AppPreferences
import com.taplink.app.domain.Card
import com.taplink.app.domain.MessageType
import com.taplink.app.domain.WalletMessage
import org.junit.Assert.*
import org.junit.Test

class PlatformPoliciesTest {
    private val card = Card("card", "Daily card", "•••• A123", 12345, false, 0, "#000000")
    private val now = 200_000_000L
    private fun message(id: String = "m1", type: MessageType = MessageType.LOW_BALANCE) =
        WalletMessage(id, "Demo reminder", "Test body", type, now - 1_000, false)

    @Test fun widgetDefaultsToHiddenEvenWhenAppBalanceIsVisible() {
        val content = WidgetContent.from(card, AppPreferences(balanceVisible = true))
        assertEquals("Balance hidden", content.balance)
        assertEquals("•••• A123", content.identifier)
        assertFalse(content.toString().contains("123.45"))
    }

    @Test fun widgetBalanceIsAnIndependentExplicitOptIn() {
        assertEquals("HK$123.45", WidgetContent.from(card, AppPreferences(balanceVisible = false, widgetBalanceVisible = true)).balance)
        assertEquals("Frozen · Demo card", WidgetContent.from(card.copy(isFrozen = true), AppPreferences()).status)
    }

    @Test fun widgetDefensivelyMasksUnmaskedInputAndHandlesMissingCard() {
        assertEquals("•••• 5678", WidgetContent.from(card.copy(maskedNumber = "12345678"), AppPreferences()).identifier)
        val empty = WidgetContent.from(null, AppPreferences(widgetBalanceVisible = true))
        assertEquals("No card selected", empty.identifier)
        assertEquals("Open TapLink to get started", empty.balance)
    }

    @Test fun disabledMasterOrTypeNeverNotifies() {
        assertFalse(ReminderPolicy.allowed(message(), AppPreferences(remindersEnabled = false), now))
        assertFalse(ReminderPolicy.allowed(message(), AppPreferences(lowBalanceAlerts = false), now))
        assertFalse(ReminderPolicy.allowed(message(type = MessageType.PASS_EXPIRY), AppPreferences(expiryRemindersEnabled = false), now))
        assertFalse(ReminderPolicy.allowed(message(type = MessageType.INFO), AppPreferences(), now))
        assertTrue(ReminderPolicy.allowed(message(type = MessageType.OFFER_EXPIRY), AppPreferences(), now))
    }

    @Test fun oldReadOrFutureMessagesDoNotNotify() {
        assertFalse(ReminderPolicy.allowed(message().copy(isRead = true), AppPreferences(), now))
        assertFalse(ReminderPolicy.allowed(message().copy(createdAt = now + 1), AppPreferences(), now))
        assertFalse(ReminderPolicy.allowed(message().copy(createdAt = now - ReminderPolicy.MAX_AGE_MILLIS - 1), AppPreferences(), now))
        assertTrue(ReminderPolicy.allowed(message().copy(createdAt = now - ReminderPolicy.MAX_AGE_MILLIS), AppPreferences(), now))
    }

    @Test fun deliveryDeduplicatesPersistentIdsAndDuplicateRows() {
        val pending = ReminderPolicy.pending(listOf(message(), message(), message("m2")), AppPreferences(), setOf("m2"), now)
        assertEquals(listOf("m1"), pending.map { it.id })
        assertTrue(ReminderPolicy.pending(pending, AppPreferences(), setOf("m1", "m2"), now).isEmpty())
    }

    @Test fun notificationBacklogIsBoundedAndNewestFirst() {
        val messages = (1..20).map { message("$it").copy(createdAt = now - it) }
        assertEquals(listOf("1", "2", "3", "4", "5"), ReminderPolicy.pending(messages.reversed(), AppPreferences(), emptySet(), now).map { it.id })
    }

    @Test fun schedulingStopsWhenAllReminderTypesAreDisabled() {
        assertFalse(ReminderPolicy.workEnabled(AppPreferences(remindersEnabled = false)))
        assertFalse(ReminderPolicy.workEnabled(AppPreferences(lowBalanceAlerts = false, expiryRemindersEnabled = false)))
        assertTrue(ReminderPolicy.workEnabled(AppPreferences(lowBalanceAlerts = false)))
    }

    @Test fun externalMapRejectsNonFiniteAndOutOfRangeCoordinates() {
        assertTrue(ExternalIntents.validCoordinates(22.336, 114.266))
        assertFalse(ExternalIntents.validCoordinates(Double.NaN, 114.0))
        assertFalse(ExternalIntents.validCoordinates(22.0, Double.POSITIVE_INFINITY))
        assertFalse(ExternalIntents.validCoordinates(91.0, 0.0))
        assertFalse(ExternalIntents.validCoordinates(0.0, -181.0))
    }
}
