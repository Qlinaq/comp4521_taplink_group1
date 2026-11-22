package com.taplink.app.domain

/** One immutable plan is computed before persistence; the repository commits the entire plan. */
data class PaymentPlan(
    val balanceAfterPaymentCents: Long,
    val reloadCents: Long,
    val finalBalanceCents: Long,
    val shouldAlert: Boolean
)

class WalletRuleException(val code: WalletError, override val message: String) : IllegalArgumentException(message)

object WalletRules {
    fun requireAmount(amountCents: Long) {
        if (amountCents !in 1..Money.MAX_BALANCE_CENTS) {
            fail(WalletError.INVALID_AMOUNT, "Enter a positive amount up to ${Money.format(Money.MAX_BALANCE_CENTS)}.")
        }
    }

    fun validatePreferences(preferences: AppPreferences) {
        if (preferences.lowBalanceThresholdCents !in 0..Money.MAX_BALANCE_CENTS ||
            preferences.autoReloadThresholdCents !in 0..Money.MAX_BALANCE_CENTS ||
            preferences.autoReloadAmountCents !in 1..Money.MAX_BALANCE_CENTS ||
            preferences.manualDistrict.isBlank() || preferences.manualDistrict.length > 80
        ) fail(WalletError.INVALID_PREFERENCES, "Check your thresholds, reload amount, and district.")
    }

    fun cleanName(name: String): String {
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > 40 || clean.any { it.isISOControl() }) {
            fail(WalletError.INVALID_NAME, "Use a card name between 1 and 40 characters.")
        }
        return clean
    }

    fun validateOperationId(operationId: String) {
        if (operationId.isBlank() || operationId.length > 128 || operationId.any { it.isISOControl() }) {
            fail(WalletError.INVALID_OPERATION_ID, "Start a new confirmation and try again.")
        }
    }

    fun credit(balanceCents: Long, frozen: Boolean, amountCents: Long): Long {
        requireAmount(amountCents)
        requireUsableCard(balanceCents, frozen)
        if (amountCents > Money.MAX_BALANCE_CENTS - balanceCents) {
            fail(WalletError.BALANCE_LIMIT, "This credit would exceed the demo balance limit.")
        }
        return balanceCents + amountCents
    }

    fun payment(balanceCents: Long, frozen: Boolean, amountCents: Long, preferences: AppPreferences): PaymentPlan {
        requireAmount(amountCents)
        requireUsableCard(balanceCents, frozen)
        validatePreferences(preferences)
        if (balanceCents < amountCents) fail(WalletError.INSUFFICIENT_BALANCE, "Insufficient demo balance. Top up before paying.")
        val afterPayment = balanceCents - amountCents
        val reload = if (preferences.autoReloadEnabled && afterPayment < preferences.autoReloadThresholdCents) {
            preferences.autoReloadAmountCents
        } else 0L
        if (reload > Money.MAX_BALANCE_CENTS - afterPayment) {
            fail(WalletError.BALANCE_LIMIT, "Automatic reload would exceed the demo balance limit. Adjust its settings.")
        }
        val finalBalance = afterPayment + reload
        return PaymentPlan(afterPayment, reload, finalBalance,
            preferences.lowBalanceAlerts && finalBalance < preferences.lowBalanceThresholdCents)
    }

    private fun requireUsableCard(balanceCents: Long, frozen: Boolean) {
        if (frozen) fail(WalletError.CARD_FROZEN, "This card is frozen. Unfreeze it to continue.")
        if (balanceCents !in 0..Money.MAX_BALANCE_CENTS) fail(WalletError.BALANCE_LIMIT, "The stored balance is outside the demo limit.")
    }

    private fun fail(code: WalletError, message: String): Nothing = throw WalletRuleException(code, message)
}
