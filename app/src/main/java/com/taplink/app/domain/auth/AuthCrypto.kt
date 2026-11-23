package com.taplink.app.domain.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** A salted, stretched password. The plaintext password is never stored in this type. */
data class StoredPassword(val salt: String, val hash: String, val iterations: Int)

interface PasswordHasher {
    fun hash(password: String): StoredPassword
    fun verify(password: String, salt: String, hash: String, iterations: Int): Boolean
}

/**
 * PBKDF2-HMAC-SHA256 with a per-account random salt. Iteration count is stored with each account so
 * it can be raised later without invalidating existing passwords.
 */
class Pbkdf2PasswordHasher(
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val random: SecureRandom = SecureRandom()
) : PasswordHasher {

    override fun hash(password: String): StoredPassword {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val derived = derive(password, salt, iterations)
        return StoredPassword(encoder.encodeToString(salt), encoder.encodeToString(derived), iterations)
    }

    override fun verify(password: String, salt: String, hash: String, iterations: Int): Boolean {
        if (iterations <= 0) return false
        val saltBytes = decodeOrNull(salt) ?: return false
        val expected = decodeOrNull(hash) ?: return false
        if (expected.isEmpty()) return false
        return MessageDigest.isEqual(expected, derive(password, saltBytes, iterations))
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun decodeOrNull(value: String): ByteArray? =
        try { decoder.decode(value) } catch (_: IllegalArgumentException) { null }

    companion object {
        const val ALGORITHM = "PBKDF2WithHmacSHA256"
        const val DEFAULT_ITERATIONS = 210_000
        private const val KEY_BITS = 256
        private const val SALT_BYTES = 16
        private val encoder: Base64.Encoder get() = Base64.getEncoder()
        private val decoder: Base64.Decoder get() = Base64.getDecoder()
    }
}

/**
 * One-time codes are stored as a salted SHA-256 digest. A code lives for five minutes, so a slow
 * KDF would buy nothing; a digest with a per-issue salt is enough to keep the database useless on
 * its own.
 */
object CodeHasher {
    private val random = SecureRandom()
    private val encoder: Base64.Encoder get() = Base64.getEncoder()

    fun newSalt(): String = ByteArray(16).also(random::nextBytes).let(encoder::encodeToString)

    fun digest(identifier: String, code: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt.toByteArray(Charsets.UTF_8))
        md.update(0)
        md.update(identifier.toByteArray(Charsets.UTF_8))
        md.update(0)
        md.update(code.toByteArray(Charsets.UTF_8))
        return encoder.encodeToString(md.digest())
    }

    fun matches(identifier: String, code: String, salt: String, expectedDigest: String): Boolean {
        val expected = try { Base64.getDecoder().decode(expectedDigest) } catch (_: IllegalArgumentException) { return false }
        val actual = try { Base64.getDecoder().decode(digest(identifier, code, salt)) } catch (_: IllegalArgumentException) { return false }
        return MessageDigest.isEqual(expected, actual)
    }
}

/** Session tokens are opaque, 256-bit random values that carry no user data. */
object TokenFactory {
    private val random = SecureRandom()
    private val encoder: Base64.Encoder get() = Base64.getUrlEncoder().withoutPadding()

    fun newToken(): String = ByteArray(32).also(random::nextBytes).let(encoder::encodeToString)
}
