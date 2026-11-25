package com.taplink.app.domain

import androidx.room.ColumnInfo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class TransactionType { PAYMENT, TOP_UP, AUTO_RELOAD, PASS_PURCHASE, SUBSIDY_CREDIT }
enum class Category { TRANSPORT, FOOD, SHOPPING, SERVICES, OTHER }
enum class LocationType { TOP_UP, MERCHANT, TRANSPORT }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class MessageType { INFO, LOW_BALANCE, PASS_EXPIRY, OFFER_EXPIRY, TRANSACTION, SERVICE }

/**
 * Structured message identity. Stored titles and bodies are English sample content, so a screen in
 * Chinese would otherwise mix languages. When [MessageKind] is present the UI renders localised
 * text from resources; legacy rows fall back to their stored text.
 */
enum class MessageKind {
    WELCOME, LOW_BALANCE, TOP_UP, PAYMENT, PASS_PURCHASE, PASS_EXPIRY, OFFER_EXPIRY, SERVICE,
    OFFER_REDEEMED
}

enum class WalletError {
    INVALID_AMOUNT, INVALID_NAME, INVALID_PREFERENCES, NOT_FOUND, CARD_FROZEN,
    INSUFFICIENT_BALANCE, BALANCE_LIMIT, IDEMPOTENCY_CONFLICT, INVALID_OPERATION_ID,
    ALREADY_REDEEMED, OFFER_EXPIRED, PASS_ALREADY_ACTIVE, INVALID_MONTH,
    ALREADY_CLAIMED, NOTHING_TO_CLAIM, STORAGE_ERROR,
    COUPON_NOT_REDEEMED, COUPON_ALREADY_USED, COUPON_WRONG_MERCHANT, COUPON_MIN_SPEND
}

sealed interface WalletResult<out T> {
    data class Success<T>(val value: T) : WalletResult<T>
    data class Error(val code: WalletError, val message: String) : WalletResult<Nothing>
}

data class Card(
    val id: String, val name: String, val maskedNumber: String,
    val balanceCents: Long, val isFrozen: Boolean, val createdAt: Long, val colorHex: String
)

data class Merchant(
    val id: String, val name: String, val category: Category,
    val district: String, val description: String
)

data class WalletTransaction(
    val id: String, val operationId: String, val cardId: String,
    val type: TransactionType, val amountCents: Long, val balanceAfterCents: Long,
    val merchantId: String?, val merchantName: String, val category: Category,
    val createdAt: Long, val note: String,
    /** Money taken off the merchant price by a redeemed coupon; already excluded from [amountCents]. */
    val discountCents: Long = 0,
    val couponId: String? = null
) {
    val isDebit: Boolean get() = type == TransactionType.PAYMENT || type == TransactionType.PASS_PURCHASE
    val signedAmountCents: Long get() = if (isDebit) -amountCents else amountCents
    /** The merchant price before the coupon: what the user was asked to pay. */
    val subtotalCents: Long get() = amountCents + discountCents
}

data class Receipt(
    val transaction: WalletTransaction,
    val autoReload: WalletTransaction?,
    val finalBalanceCents: Long,
    val pass: TransportPass? = null
) {
    fun shareText(): String = buildString {
        appendLine("TapLink simulated receipt")
        appendLine("Educational prototype — no real money or tickets.")
        appendLine("Receipt: ${transaction.id}")
        appendLine("Operation: ${transaction.operationId}")
        appendLine("Card: ${transaction.cardId}")
        appendLine("Type: ${transaction.type.name.replace('_', ' ')}")
        appendLine("Merchant: ${transaction.merchantName}")
        appendLine("Amount: ${Money.format(transaction.amountCents)}")
        if (transaction.discountCents > 0) {
            appendLine("Coupon saving: ${Money.format(transaction.discountCents)} off ${Money.format(transaction.subtotalCents)}")
        }
        appendLine("Time: ${receiptTime.format(Instant.ofEpochMilli(transaction.createdAt))}")
        autoReload?.let { appendLine("Automatic demo reload: ${Money.format(it.amountCents)}") }
        pass?.let { appendLine("Demo pass valid until: ${receiptTime.format(Instant.ofEpochMilli(it.validUntil))}") }
        append("Resulting balance: ${Money.format(finalBalanceCents)}")
    }

    private companion object {
        val receiptTime: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withZone(ZoneId.of("Asia/Hong_Kong"))
    }
}

data class AppPreferences(
    val selectedCardId: String? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    @ColumnInfo(defaultValue = "ENGLISH") val language: AppLanguage = AppLanguage.ENGLISH,
    val balanceVisible: Boolean = true,
    val widgetBalanceVisible: Boolean = false,
    val lowBalanceAlerts: Boolean = true,
    val lowBalanceThresholdCents: Long = 5000,
    val autoReloadEnabled: Boolean = false,
    val autoReloadThresholdCents: Long = 5000,
    val autoReloadAmountCents: Long = 10000,
    val remindersEnabled: Boolean = true,
    val expiryRemindersEnabled: Boolean = true,
    val demoRefreshEnabled: Boolean = true,
    val shakeToToggleEnabled: Boolean = false,
    val manualDistrict: String = "Sai Kung",
    // Notification categories. [notificationsEnabled] is the master switch; the rest refine it.
    val notificationsEnabled: Boolean = true,
    val transactionAlerts: Boolean = true,
    val offerAlerts: Boolean = true,
    val serviceAnnouncements: Boolean = true,
    // Accessibility and elderly-friendly preferences.
    val elderlyMode: Boolean = false,
    val largeText: Boolean = false,
    val highContrast: Boolean = false,
    val voiceReading: Boolean = false
)

data class WalletMessage(
    val id: String, val title: String, val body: String,
    val type: MessageType, val createdAt: Long, val isRead: Boolean,
    val cardId: String? = null, val transactionId: String? = null,
    /** Null for rows created before the structured-message migration. */
    val kind: MessageKind? = null,
    /** Amount referenced by a transactional message, in cents. */
    val amountCents: Long? = null
)

data class ServiceLocation(
    val id: String, val name: String, val type: LocationType,
    val district: String, val address: String, val latitude: Double, val longitude: Double,
    val openingHours: String, val description: String
)

data class TransportPass(
    val id: String, val cardId: String, val name: String,
    val priceCents: Long, val validFrom: Long, val validUntil: Long, val transactionId: String
) {
    fun isActive(now: Long = System.currentTimeMillis()): Boolean = now >= validFrom && now < validUntil
}

/**
 * How a demo coupon reduces a payment. [FIXED] takes a flat amount off, [PERCENT] takes a share of
 * the price (optionally capped), and a non-zero minimum spend turns either into a spend-threshold
 * coupon. [NONE] keeps catalogue rows that were written before coupons could be spent.
 */
enum class CouponKind { NONE, FIXED, PERCENT }

/** Redemption and spending are separate: a coupon is redeemed into the wallet, then spent once. */
enum class CouponStatus { UNREDEEMED, USABLE, USED, EXPIRED }

data class Offer(
    val id: String, val merchantId: String, val title: String,
    val description: String, val terms: String, val expiresAt: Long,
    val isFavorite: Boolean = false, val redeemedAt: Long? = null, val couponCode: String? = null,
    val discountKind: CouponKind = CouponKind.NONE,
    /** Cents off for [CouponKind.FIXED]; whole percent off for [CouponKind.PERCENT]. */
    val discountValue: Int = 0,
    /** Minimum merchant price required before the coupon applies; 0 means no minimum. */
    val minSpendCents: Long = 0,
    /** Cap for [CouponKind.PERCENT]; 0 means uncapped. */
    val maxDiscountCents: Long = 0,
    /** Set when the coupon was spent at a payment; a coupon is never spent twice. */
    val usedAt: Long? = null,
    val usedTransactionId: String? = null
) {
    fun status(now: Long = System.currentTimeMillis()): CouponStatus = when {
        usedAt != null -> CouponStatus.USED
        expiresAt <= now -> CouponStatus.EXPIRED
        redeemedAt != null -> CouponStatus.USABLE
        else -> CouponStatus.UNREDEEMED
    }

    /** Spends at most [subtotalCents] minus one cent, so a demo payment always charges something. */
    fun discountFor(subtotalCents: Long): Long {
        if (subtotalCents <= 0 || discountKind == CouponKind.NONE) return 0
        if (minSpendCents > 0 && subtotalCents < minSpendCents) return 0
        val raw = when (discountKind) {
            CouponKind.FIXED -> discountValue.toLong()
            CouponKind.PERCENT -> subtotalCents * discountValue.coerceIn(0, 100) / 100
            CouponKind.NONE -> 0L
        }
        val capped = if (maxDiscountCents > 0) minOf(raw, maxDiscountCents) else raw
        return capped.coerceIn(0L, (subtotalCents - 1).coerceAtLeast(0L))
    }
}

data class SubsidyClaim(
    val id: String, val cardId: String, val month: String,
    val transportSpendingCents: Long, val amountCents: Long, val claimedAt: Long, val transactionId: String
)

data class TransactionFilter(
    val cardId: String? = null, val category: Category? = null,
    val type: TransactionType? = null, val fromInclusive: Long? = null,
    val toExclusive: Long? = null, val query: String = ""
)

data class SpendingSummary(val totalCents: Long, val byCategory: Map<Category, Long>)

object PassCatalog {
    const val MONTHLY_PASS_PRICE_CENTS: Long = 30000L
    const val MONTHLY_PASS_NAME: String = "TapLink Demo Monthly Pass"
}
