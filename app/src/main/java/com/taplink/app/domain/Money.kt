package com.taplink.app.domain

import java.math.BigDecimal
import java.math.BigInteger

object Money {
    const val MAX_BALANCE_CENTS: Long = 100_000_000L
    private val decimal = Regex("[0-9]+(?:\\.[0-9]{1,2})?")

    /** Never accepts exponent notation, signs, grouping, or fractional cents. */
    fun parseCents(input: String): Long? {
        val text = input.trim()
        if (text.length > 24 || !decimal.matches(text)) return null
        return try {
            BigDecimal(text).movePointRight(2).longValueExact()
                .takeIf { it in 1..MAX_BALANCE_CENTS }
        } catch (_: ArithmeticException) {
            null
        }
    }

    fun format(cents: Long): String {
        // BigInteger also handles Long.MIN_VALUE without an absolute-value overflow.
        val absolute = BigInteger.valueOf(cents).abs()
        val parts = absolute.divideAndRemainder(BigInteger.valueOf(100))
        return "${if (cents < 0) "-" else ""}HK$${parts[0]}.${parts[1].toString().padStart(2, '0')}"
    }
}
