package com.taplink.app.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    private val hasher = Pbkdf2PasswordHasher(iterations = 1_000)

    @Test
    fun `hash then verify accepts the same password`() {
        val stored = hasher.hash("Maple-River-42")
        assertTrue(hasher.verify("Maple-River-42", stored.salt, stored.hash, stored.iterations))
    }

    @Test
    fun `verify rejects a different password`() {
        val stored = hasher.hash("Maple-River-42")
        assertFalse(hasher.verify("Maple-River-43", stored.salt, stored.hash, stored.iterations))
        assertFalse(hasher.verify("", stored.salt, stored.hash, stored.iterations))
    }

    @Test
    fun `plaintext never appears in the stored value`() {
        val stored = hasher.hash("Maple-River-42")
        assertFalse(stored.hash.contains("Maple"))
        assertFalse(stored.salt.contains("Maple"))
        assertNotEquals("Maple-River-42", stored.hash)
    }

    @Test
    fun `every hash uses a fresh salt and therefore a different digest`() {
        val first = hasher.hash("Maple-River-42")
        val second = hasher.hash("Maple-River-42")
        assertNotEquals(first.salt, second.salt)
        assertNotEquals(first.hash, second.hash)
        assertTrue(hasher.verify("Maple-River-42", first.salt, first.hash, first.iterations))
        assertTrue(hasher.verify("Maple-River-42", second.salt, second.hash, second.iterations))
    }

    @Test
    fun `cross pairing a salt with another digest fails`() {
        val first = hasher.hash("Maple-River-42")
        val second = hasher.hash("Maple-River-42")
        assertFalse(hasher.verify("Maple-River-42", first.salt, second.hash, first.iterations))
    }

    @Test
    fun `the stored iteration count is honoured`() {
        val stored = hasher.hash("Maple-River-42")
        assertEquals(1_000, stored.iterations)
        assertFalse(hasher.verify("Maple-River-42", stored.salt, stored.hash, 2_000))
    }

    @Test
    fun `malformed stored values are rejected instead of throwing`() {
        val stored = hasher.hash("Maple-River-42")
        assertFalse(hasher.verify("Maple-River-42", "not base64 !!", stored.hash, stored.iterations))
        assertFalse(hasher.verify("Maple-River-42", stored.salt, "not base64 !!", stored.iterations))
        assertFalse(hasher.verify("Maple-River-42", stored.salt, "", stored.iterations))
        assertFalse(hasher.verify("Maple-River-42", stored.salt, stored.hash, 0))
    }

    @Test
    fun `default iterations meet the documented floor`() {
        assertTrue(Pbkdf2PasswordHasher.DEFAULT_ITERATIONS >= 210_000)
        assertEquals("PBKDF2WithHmacSHA256", Pbkdf2PasswordHasher.ALGORITHM)
    }

    @Test
    fun `code digest is salted and one way`() {
        val salt = CodeHasher.newSalt()
        val digest = CodeHasher.digest("ziyao@ust.hk", "123456", salt)
        assertFalse(digest.contains("123456"))
        assertTrue(CodeHasher.matches("ziyao@ust.hk", "123456", salt, digest))
        assertFalse(CodeHasher.matches("ziyao@ust.hk", "123457", salt, digest))
        assertFalse(CodeHasher.matches("other@ust.hk", "123456", salt, digest))
    }

    @Test
    fun `code digest depends on the salt`() {
        val first = CodeHasher.digest("ziyao@ust.hk", "123456", CodeHasher.newSalt())
        val second = CodeHasher.digest("ziyao@ust.hk", "123456", CodeHasher.newSalt())
        assertNotEquals(first, second)
    }

    @Test
    fun `code digest rejects malformed stored digests`() {
        assertFalse(CodeHasher.matches("a@b.hk", "123456", CodeHasher.newSalt(), "!! not base64"))
    }

    @Test
    fun `session tokens are long unique and url safe`() {
        val first = TokenFactory.newToken()
        val second = TokenFactory.newToken()
        assertNotEquals(first, second)
        assertTrue(first.length >= 40)
        assertTrue(first.all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }
}
