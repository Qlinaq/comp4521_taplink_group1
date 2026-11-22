package com.taplink.app.domain

import java.time.Instant
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class WalletAnalyticsTest {
    @Test fun spendingIncludesOnlyDebitTypes() {
        val records = listOf(
            record("p", TransactionType.PAYMENT, 101, Category.FOOD),
            record("pass", TransactionType.PASS_PURCHASE, 30000, Category.TRANSPORT),
            record("top", TransactionType.TOP_UP, 50000),
            record("reload", TransactionType.AUTO_RELOAD, 10000),
            record("subsidy", TransactionType.SUBSIDY_CREDIT, 1000))
        val summary = WalletAnalytics.spending(records)
        assertEquals(30101, summary.totalCents)
        assertEquals(mapOf(Category.FOOD to 101L, Category.TRANSPORT to 30000L), summary.byCategory)
    }

    @Test fun filtersCombineCardCategoryTypeDateAndCaseInsensitiveQuery() {
        val records = listOf(
            record("first", at = 1000), record("match", at = 2000), record("exclusive", at = 3000),
            record("other-card", at = 2000).copy(cardId = "other"),
            record("other-category", at = 2000, category = Category.FOOD),
            record("other-type", at = 2000, type = TransactionType.TOP_UP),
            record("other-merchant", at = 2000).copy(merchantName = "Bay Books"))
        val filter = TransactionFilter("card", Category.TRANSPORT, TransactionType.PAYMENT, 2000, 3000, " METRO ")
        assertEquals(listOf("match"), WalletAnalytics.filter(records, filter).map { it.id })
    }

    @Test fun monthlyTotalsUseHongKongBoundaries() {
        val records = listOf(
            record("before", at = Instant.parse("2026-10-31T15:59:59Z").toEpochMilli()),
            record("start", at = Instant.parse("2026-10-31T16:00:00Z").toEpochMilli()),
            record("last", at = Instant.parse("2026-11-30T15:59:59Z").toEpochMilli()),
            record("end", at = Instant.parse("2026-11-30T16:00:00Z").toEpochMilli()))
        assertEquals(200, WalletAnalytics.monthlySpending(records, YearMonth.of(2026, 11)).totalCents)
    }

    @Test fun fictionalSubsidyRoundsDownAndCapsWithoutOverflow() {
        assertEquals(0, WalletAnalytics.fictionalSubsidy(20000))
        assertEquals(0, WalletAnalytics.fictionalSubsidy(20004))
        assertEquals(1, WalletAnalytics.fictionalSubsidy(20005))
        assertEquals(3000, WalletAnalytics.fictionalSubsidy(35000))
        assertEquals(10000, WalletAnalytics.fictionalSubsidy(Long.MAX_VALUE))
        assertEquals(0, WalletAnalytics.fictionalSubsidy(Long.MIN_VALUE))
    }

    @Test fun passValidityIsStartInclusiveAndEndExclusive() {
        val pass = TransportPass("pass", "card", "Demo", 30000, 1000, 2000, "txn")
        assertFalse(pass.isActive(999))
        assertTrue(pass.isActive(1000))
        assertTrue(pass.isActive(1999))
        assertFalse(pass.isActive(2000))
    }

    @Test fun receiptContainsStoredAmountReloadAndFinalBalance() {
        val payment = record("payment", amount = 2000).copy(operationId = "confirmation", balanceAfterCents = 4000)
        val reload = record("reload", TransactionType.AUTO_RELOAD, 10000).copy(balanceAfterCents = 14000)
        val text = Receipt(payment, reload, 14000).shareText()
        listOf("simulated", "HK$20.00", "HK$100.00", "HK$140.00", "confirmation", "payment").forEach {
            assertTrue("Missing $it from receipt", text.contains(it))
        }
        assertEquals(-2000, payment.signedAmountCents)
        assertEquals(10000, reload.signedAmountCents)
    }

    private fun record(id: String, type: TransactionType = TransactionType.PAYMENT,
        amount: Long = 100, category: Category = Category.TRANSPORT, at: Long = 0) =
        WalletTransaction(id, id, "card", type, amount, 10000, "metro", "Harbour Metro", category, at, "Demo")
}
