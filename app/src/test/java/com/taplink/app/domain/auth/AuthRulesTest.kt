package com.taplink.app.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRulesTest {

    @Test
    fun `email is trimmed and lower-cased`() {
        assertEquals("ziyao@ust.hk", AuthRules.normalizeEmail("  Ziyao@UST.HK "))
    }

    @Test
    fun `email rejects malformed input`() {
        listOf("", "  ", "ziyao", "ziyao@", "@ust.hk", "a@b", "a@@b.com", "a..b@ust.hk", ".a@ust.hk", "a@ust..hk")
            .forEach { assertNull("expected rejection for <$it>", AuthRules.normalizeEmail(it)) }
    }

    @Test
    fun `email accepts plus addressing and subdomains`() {
        assertEquals("a.b+tag@mail.ust.hk", AuthRules.normalizeEmail("A.B+tag@mail.ust.hk"))
    }

    @Test
    fun `eight digit hong kong number gains the country code`() {
        assertEquals("+85291234567", AuthRules.normalizePhone("9123 4567"))
        assertEquals("+85291234567", AuthRules.normalizePhone("9123-4567"))
        assertEquals("+85223456789", AuthRules.normalizePhone("(2345) 6789"))
    }

    @Test
    fun `eleven digit form with 852 prefix is accepted`() {
        assertEquals("+85291234567", AuthRules.normalizePhone("85291234567"))
    }

    @Test
    fun `international numbers must already be e164`() {
        assertEquals("+8613800138000", AuthRules.normalizePhone("+86 138 0013 8000"))
        assertNull(AuthRules.normalizePhone("13800138000"))
    }

    @Test
    fun `phone rejects impossible shapes`() {
        listOf("", "abc", "1234567", "123456789", "1234-56789012", "０９１２３４５６７", "9123456789012")
            .forEach { assertNull("expected rejection for <$it>", AuthRules.normalizePhone(it)) }
    }

    @Test
    fun `channel is detected from the shape of the input`() {
        assertEquals(AuthChannel.EMAIL to "a@ust.hk", AuthRules.normalizeIdentifier("A@ust.hk"))
        assertEquals(AuthChannel.PHONE to "+85291234567", AuthRules.normalizeIdentifier("9123 4567"))
        assertNull(AuthRules.normalizeIdentifier("   "))
    }

    @Test
    fun `masking keeps a hint without leaking the address`() {
        assertEquals("z***@ust.hk", AuthRules.maskEmail("ziyao@ust.hk"))
        assertEquals("+852 •••• 4567", AuthRules.maskPhone("+85291234567"))
        assertEquals("z***@ust.hk", AuthRules.maskIdentifier("ziyao@ust.hk"))
        assertEquals("+852 •••• 4567", AuthRules.maskIdentifier("+85291234567"))
    }

    @Test
    fun `password policy reports the first failed rule`() {
        val id = "ziyao@ust.hk"
        assertEquals(PasswordIssue.TOO_SHORT, AuthRules.passwordProblem("Ab1", id))
        assertEquals(PasswordIssue.TOO_LONG, AuthRules.passwordProblem("a1" + "x".repeat(200), id))
        assertEquals(PasswordIssue.MISSING_LETTER, AuthRules.passwordProblem("12345678", id))
        assertEquals(PasswordIssue.MISSING_DIGIT, AuthRules.passwordProblem("abcdefghij", id))
        assertEquals(PasswordIssue.TOO_COMMON, AuthRules.passwordProblem("password123", id))
        assertEquals(PasswordIssue.CONTAINS_IDENTIFIER, AuthRules.passwordProblem("ziyao-2026", id))
        assertNull(AuthRules.passwordProblem("Maple-River-42", id))
    }

    @Test
    fun `common password list is matched case insensitively`() {
        assertEquals(PasswordIssue.TOO_COMMON, AuthRules.passwordProblem("PassWord123", "someone@ust.hk"))
    }

    @Test
    fun `short local part cannot trigger the identifier rule`() {
        assertNull(AuthRules.passwordProblem("Abcdefg1", "ab@ust.hk"))
    }

    @Test
    fun `otp policy exposes its budgets`() {
        assertEquals(300_000L, OtpPolicy.TTL_MS)
        assertTrue(OtpPolicy.isExpired(OtpPolicy.expiresAt(1_000L), 1_000L + OtpPolicy.TTL_MS))
        assertTrue(!OtpPolicy.isExpired(OtpPolicy.expiresAt(1_000L), 1_000L + OtpPolicy.TTL_MS - 1))
        assertEquals(60_000L, OtpPolicy.resendAvailableAt(0L))
        assertTrue(OtpPolicy.isWellFormed("012345"))
        assertTrue(!OtpPolicy.isWellFormed("01234"))
        assertTrue(!OtpPolicy.isWellFormed("01234a"))
    }

    @Test
    fun `generated codes keep leading zeros`() {
        val fixed = object : java.security.SecureRandom() {
            override fun nextInt(bound: Int): Int = 5
        }
        assertEquals("000005", OtpPolicy.generate(fixed))
        assertEquals(OtpPolicy.LENGTH, OtpPolicy.generate().length)
        assertTrue(OtpPolicy.generate().all { it in '0'..'9' })
    }

    @Test
    fun `lockout escalates then caps`() {
        listOf(0, 1, 4).forEach { assertEquals(0L, LockoutPolicy.lockDurationMs(it)) }
        assertEquals(60_000L, LockoutPolicy.lockDurationMs(5))
        assertEquals(120_000L, LockoutPolicy.lockDurationMs(6))
        assertEquals(240_000L, LockoutPolicy.lockDurationMs(7))
        assertEquals(300_000L, LockoutPolicy.lockDurationMs(8))
        assertEquals(300_000L, LockoutPolicy.lockDurationMs(99))
    }

    @Test
    fun `lock state compares against the clock`() {
        assertTrue(LockoutPolicy.isLocked(5_000L, 4_999L))
        assertTrue(!LockoutPolicy.isLocked(5_000L, 5_000L))
        assertTrue(!LockoutPolicy.isLocked(null, 0L))
    }

    @Test
    fun `session expires after thirty days`() {
        val session = SessionPolicy.create("acc", "token", 1_000L)
        assertEquals(1_000L + SessionPolicy.LIFETIME_MS, session.expiresAt)
        assertTrue(!session.isExpired(session.expiresAt - 1))
        assertTrue(session.isExpired(session.expiresAt))
    }
}
