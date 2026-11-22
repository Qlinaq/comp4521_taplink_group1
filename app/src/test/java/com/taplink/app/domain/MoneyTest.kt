package com.taplink.app.domain

import org.junit.Assert.*
import org.junit.Test

class MoneyTest {
    @Test fun decimalAmountsAreParsedExactly() {
        assertEquals(1L, Money.parseCents("0.01"))
        assertEquals(29L, Money.parseCents("0.29"))
        assertEquals(1010L, Money.parseCents("10.1"))
        assertEquals(12345L, Money.parseCents(" 123.45 "))
        assertEquals(Money.MAX_BALANCE_CENTS, Money.parseCents("1000000.00"))
    }

    @Test fun ambiguousInvalidAndOverflowInputsAreRejected() {
        listOf("", " ", "0", "0.00", "-1", "+1", "1.001", "1e2", "NaN", "Infinity", "1,000",
            ".50", "1.", "1 00", "1000000.01", "9223372036854775808", "999999999999999999999999999999999")
            .forEach { assertNull("Unexpectedly accepted '$it'", Money.parseCents(it)) }
    }

    @Test fun formatNeverUsesFloatingPointOrLocaleDependentGrouping() {
        assertEquals("HK$0.00", Money.format(0))
        assertEquals("HK$0.01", Money.format(1))
        assertEquals("HK$123.45", Money.format(12345))
        assertEquals("-HK$0.01", Money.format(-1))
        assertEquals("-HK$92233720368547758.08", Money.format(Long.MIN_VALUE))
    }
}
