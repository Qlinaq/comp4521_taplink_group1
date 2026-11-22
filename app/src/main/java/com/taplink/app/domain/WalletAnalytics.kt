package com.taplink.app.domain

import java.time.YearMonth
import java.time.ZoneId

object WalletAnalytics {
    fun filter(transactions: List<WalletTransaction>, filter: TransactionFilter): List<WalletTransaction> {
        val query = filter.query.trim()
        return transactions.filter { transaction ->
            (filter.cardId == null || transaction.cardId == filter.cardId) &&
                (filter.category == null || transaction.category == filter.category) &&
                (filter.type == null || transaction.type == filter.type) &&
                (filter.fromInclusive == null || transaction.createdAt >= filter.fromInclusive) &&
                (filter.toExclusive == null || transaction.createdAt < filter.toExclusive) &&
                transaction.merchantName.contains(query, ignoreCase = true)
        }
    }

    fun spending(transactions: List<WalletTransaction>): SpendingSummary {
        val byCategory = transactions.filter { it.isDebit }.groupBy { it.category }
            .mapValues { (_, records) -> records.fold(0L) { total, record -> Math.addExact(total, record.amountCents) } }
        return SpendingSummary(byCategory.values.fold(0L, Math::addExact), byCategory)
    }

    fun monthlySpending(
        transactions: List<WalletTransaction>, month: YearMonth,
        zone: ZoneId = ZoneId.of("Asia/Hong_Kong")
    ): SpendingSummary {
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return spending(filter(transactions, TransactionFilter(fromInclusive = start, toExclusive = end)))
    }

    /** Fictional demo: 20% above HK$200, capped at HK$100; never an official entitlement. */
    fun fictionalSubsidy(transportSpendingCents: Long): Long =
        if (transportSpendingCents <= 20000L) 0L else ((transportSpendingCents - 20000L) / 5L).coerceAtMost(10000L)
}
