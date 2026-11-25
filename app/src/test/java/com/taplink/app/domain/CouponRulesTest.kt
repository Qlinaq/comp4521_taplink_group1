package com.taplink.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coupons are the only place where the amount charged differs from the amount entered, so the
 * arithmetic is pinned here instead of being trusted in the payment screen.
 */
class CouponRulesTest {

    private fun coupon(
        kind: CouponKind,
        value: Int,
        minSpend: Long = 0,
        cap: Long = 0,
        redeemedAt: Long? = 1,
        usedAt: Long? = null,
        expiresAt: Long = 100_000
    ) = Offer(
        id = "c", merchantId = "m", title = "t", description = "d", terms = "x", expiresAt = expiresAt,
        redeemedAt = redeemedAt, usedAt = usedAt,
        discountKind = kind, discountValue = value, minSpendCents = minSpend, maxDiscountCents = cap
    )

    @Test fun flatCouponTakesItsAmountOff() {
        assertEquals(800L, coupon(CouponKind.FIXED, 800).discountFor(4250))
    }

    @Test fun flatCouponNeverMakesThePaymentFree() {
        // HK$8 off an HK$5 order would charge nothing; the demo ledger needs a positive amount.
        assertEquals(499L, coupon(CouponKind.FIXED, 800).discountFor(500))
        assertEquals(0L, coupon(CouponKind.FIXED, 800).discountFor(0))
    }

    @Test fun percentCouponRoundsDownToWholeCents() {
        assertEquals(425L, coupon(CouponKind.PERCENT, 10).discountFor(4255))
        assertEquals(600L, coupon(CouponKind.PERCENT, 15).discountFor(4000))
    }

    @Test fun percentCouponIsCapped() {
        assertEquals(2000L, coupon(CouponKind.PERCENT, 15, cap = 2000).discountFor(20000))
        assertEquals(600L, coupon(CouponKind.PERCENT, 15, cap = 2000).discountFor(4000))
    }

    @Test fun minimumSpendMustBeReachedBeforeAnythingIsTaken() {
        val threshold = coupon(CouponKind.FIXED, 3000, minSpend = 20000)
        assertEquals(0L, threshold.discountFor(19999))
        assertEquals(3000L, threshold.discountFor(20000))
        assertEquals(3000L, threshold.discountFor(50000))
    }

    @Test fun aCouponWithoutARuleTakesNothing() {
        assertEquals(0L, coupon(CouponKind.NONE, 500).discountFor(5000))
    }

    @Test fun statusSeparatesRedeemingFromSpending() {
        val now = 2_000L
        assertEquals(CouponStatus.UNREDEEMED, coupon(CouponKind.FIXED, 100, redeemedAt = null).status(now))
        // Past its date, an unredeemed coupon is expired: it cannot be redeemed any more either.
        assertEquals(CouponStatus.EXPIRED, coupon(CouponKind.FIXED, 100, redeemedAt = null, expiresAt = 1_500).status(now))
        assertEquals(CouponStatus.USABLE, coupon(CouponKind.FIXED, 100).status(now))
        assertEquals(CouponStatus.EXPIRED, coupon(CouponKind.FIXED, 100, expiresAt = now).status(now))
        assertEquals(CouponStatus.USED, coupon(CouponKind.FIXED, 100, usedAt = 1_500).status(now))
    }

    @Test fun aSpentCouponStaysUsedEvenAfterItsExpiryDate() {
        val spent = coupon(CouponKind.FIXED, 100, usedAt = 1_500, expiresAt = 1_600)
        assertTrue(spent.status(9_999) == CouponStatus.USED)
    }
}
