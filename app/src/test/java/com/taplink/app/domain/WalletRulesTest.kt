package com.taplink.app.domain

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class WalletRulesTest {
    private val defaults = AppPreferences()

    @Test fun topUpIsAnExactCredit() {
        assertEquals(1030L, WalletRules.credit(1001, false, 29))
        assertEquals(Money.MAX_BALANCE_CENTS, WalletRules.credit(Money.MAX_BALANCE_CENTS - 1, false, 1))
    }

    @Test fun creditsRejectOverflowBeforeAdding() {
        rejects(WalletError.BALANCE_LIMIT) { WalletRules.credit(Money.MAX_BALANCE_CENTS, false, 1) }
        rejects(WalletError.INVALID_AMOUNT) { WalletRules.credit(1, false, Long.MAX_VALUE) }
        rejects(WalletError.BALANCE_LIMIT) { WalletRules.credit(-1, false, 1) }
    }

    @Test fun allMoneyOperationsRejectInvalidAmounts() {
        for (amount in listOf(0L, -1L, Long.MIN_VALUE, Money.MAX_BALANCE_CENTS + 1)) {
            rejects(WalletError.INVALID_AMOUNT) { WalletRules.credit(100, false, amount) }
            rejects(WalletError.INVALID_AMOUNT) { WalletRules.payment(100, false, amount, defaults) }
        }
    }

    @Test fun frozenCardsRejectCreditsAndDebits() {
        rejects(WalletError.CARD_FROZEN) { WalletRules.credit(10000, true, 100) }
        rejects(WalletError.CARD_FROZEN) { WalletRules.payment(10000, true, 100, defaults) }
    }

    @Test fun exactBalancePaymentCanReachZero() {
        assertEquals(PaymentPlan(0, 0, 0, true), WalletRules.payment(29, false, 29, defaults))
    }

    @Test fun automaticReloadCannotRescueAnUnaffordablePayment() {
        rejects(WalletError.INSUFFICIENT_BALANCE) {
            WalletRules.payment(99, false, 100, defaults.copy(autoReloadEnabled = true, autoReloadAmountCents = 10000))
        }
    }

    @Test fun exactlyAtThresholdDoesNotReloadOrAlert() {
        val plan = WalletRules.payment(6000, false, 1000, defaults.copy(autoReloadEnabled = true))
        assertEquals(PaymentPlan(5000, 0, 5000, false), plan)
    }

    @Test fun oneReloadMayLeaveBalanceBelowThresholdWithoutLooping() {
        val plan = WalletRules.payment(2000, false, 1500, defaults.copy(
            autoReloadEnabled = true, autoReloadAmountCents = 100))
        assertEquals(PaymentPlan(500, 100, 600, true), plan)
    }

    @Test fun alertUsesBalanceAfterReload() {
        val plan = WalletRules.payment(6000, false, 2000, defaults.copy(autoReloadEnabled = true))
        assertEquals(PaymentPlan(4000, 10000, 14000, false), plan)
    }

    @Test fun alertPreferenceIsRespected() {
        assertFalse(WalletRules.payment(100, false, 100, defaults.copy(lowBalanceAlerts = false)).shouldAlert)
    }

    @Test fun reloadOverflowRejectsEntirePlan() {
        rejects(WalletError.BALANCE_LIMIT) {
            WalletRules.payment(1000, false, 100, defaults.copy(autoReloadEnabled = true,
                autoReloadAmountCents = Money.MAX_BALANCE_CENTS))
        }
    }

    @Test fun invalidPreferencesAreRejectedEvenWhenReloadDisabled() {
        listOf(defaults.copy(lowBalanceThresholdCents = -1), defaults.copy(autoReloadThresholdCents = -1),
            defaults.copy(autoReloadAmountCents = 0), defaults.copy(autoReloadAmountCents = Long.MAX_VALUE),
            defaults.copy(manualDistrict = " ")).forEach {
            rejects(WalletError.INVALID_PREFERENCES) { WalletRules.validatePreferences(it) }
        }
    }

    @Test fun operationIdsAndCardNamesAreValidated() {
        assertEquals("Campus", WalletRules.cleanName("  Campus  "))
        listOf("", " ", "a".repeat(41), "a\nb").forEach { name ->
            rejects(WalletError.INVALID_NAME) { WalletRules.cleanName(name) }
        }
        listOf("", " ", "a".repeat(129), "a\nb").forEach { id ->
            rejects(WalletError.INVALID_OPERATION_ID) { WalletRules.validateOperationId(id) }
        }
        WalletRules.validateOperationId("b3baa95f-51bf-4e8a-bf63-45f4e9ba86b2")
    }

    @Test fun randomizedAffordablePaymentsConserveEveryCent() {
        val random = Random(4521)
        repeat(10000) {
            val balance = random.nextLong(1, 1_000_000)
            val amount = random.nextLong(1, balance + 1)
            val prefs = defaults.copy(autoReloadEnabled = random.nextBoolean(),
                autoReloadThresholdCents = random.nextLong(0, 100000),
                autoReloadAmountCents = random.nextLong(1, 100000))
            val plan = WalletRules.payment(balance, false, amount, prefs)
            assertEquals(balance - amount, plan.balanceAfterPaymentCents)
            assertEquals(balance - amount + plan.reloadCents, plan.finalBalanceCents)
            assertTrue(plan.reloadCents == 0L || plan.reloadCents == prefs.autoReloadAmountCents)
            assertTrue(plan.finalBalanceCents >= 0)
            assertEquals(plan.finalBalanceCents < prefs.lowBalanceThresholdCents, plan.shouldAlert)
        }
    }

    private fun rejects(code: WalletError, operation: () -> Unit) {
        try { operation(); fail("Expected $code") }
        catch (error: WalletRuleException) { assertEquals(code, error.code) }
    }
}
