package com.taplink.app.domain.auth

import java.security.SecureRandom
import java.util.Locale

/** Why a candidate password was rejected. Distinct values let the UI give precise guidance. */
enum class PasswordIssue { TOO_SHORT, TOO_LONG, MISSING_LETTER, MISSING_DIGIT, CONTAINS_IDENTIFIER, TOO_COMMON }

/**
 * Pure identifier, password and code policy. Deliberately free of Android types so every rule
 * below is covered by a host-side unit test rather than an instrumentation test.
 */
object AuthRules {
    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_PASSWORD_LENGTH = 128

    private val EMAIL = Regex("^[A-Za-z0-9._%+\\-]{1,64}@[A-Za-z0-9\\-]{1,63}(?:\\.[A-Za-z0-9\\-]{1,63})+$")
    private val E164 = Regex("^\\+[1-9]\\d{7,14}$")

    /** Passwords that pass the shape rules but are still trivially guessable. */
    private val COMMON = setOf(
        "password1", "password12", "password123", "passw0rd", "p@ssw0rd", "12345678", "123456789",
        "1234567890", "qwerty123", "qwertyui", "abc12345", "abcd1234", "abcd12345", "iloveyou1",
        "taplink1", "taplink12", "taplink123", "11111111", "00000000", "letmein1", "welcome1",
        "admin123", "sunshine1", "football1", "monkey123"
    )

    /** Lower-cased, trimmed address, or null when the text is not a usable email. */
    fun normalizeEmail(raw: String): String? {
        val text = raw.trim().lowercase(Locale.ROOT)
        if (text.length !in 6..254) return null
        if (!EMAIL.matches(text)) return null
        val local = text.substringBefore('@')
        if (local.startsWith('.') || local.endsWith('.') || ".." in text) return null
        return text
    }

    /**
     * Hong Kong numbers are the only ones we shorten: eight digits become +852…, an eleven-digit
     * 852… form is accepted, anything else must already be E.164.
     */
    fun normalizePhone(raw: String): String? {
        val stripped = raw.filter { !it.isWhitespace() && it !in "()-." }
        if (stripped.isEmpty()) return null
        val digits = stripped.removePrefix("+")
        if (digits.isEmpty() || !digits.all { it in '0'..'9' }) return null
        val e164 = when {
            stripped.startsWith("+") -> stripped
            digits.length == 8 && digits.first() in '2'..'9' -> "+852$digits"
            digits.length == 11 && digits.startsWith("852") && digits[3] in '2'..'9' -> "+$digits"
            else -> null
        } ?: return null
        return e164.takeIf { E164.matches(it) }
    }

    /** Detects the channel from the shape of the input: an address or a phone number. */
    fun normalizeIdentifier(raw: String): Pair<AuthChannel, String>? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        if ('@' in text) return normalizeEmail(text)?.let { AuthChannel.EMAIL to it }
        return normalizePhone(text)?.let { AuthChannel.PHONE to it }
    }

    /** `w***@ust.hk` — enough to recognise, not enough to leak the address. */
    fun maskEmail(email: String): String {
        val local = email.substringBefore('@')
        val domain = email.substringAfter('@', "")
        val head = if (local.length >= 2) local.take(1) else ""
        return "$head***@$domain"
    }

    /** `+852 •••• 4567` */
    fun maskPhone(phone: String): String {
        val digits = phone.filter { it in '0'..'9' }
        val prefix = phone.substringBefore(digits.takeLast(8)).ifEmpty { "+" }
        return "$prefix •••• ${digits.takeLast(4)}"
    }

    fun maskIdentifier(identifier: String): String =
        if ('@' in identifier) maskEmail(identifier) else maskPhone(identifier)

    /** Returns the first failed rule, or null when the password is acceptable. */
    fun passwordProblem(password: String, identifier: String): PasswordIssue? {
        if (password.length < MIN_PASSWORD_LENGTH) return PasswordIssue.TOO_SHORT
        if (password.length > MAX_PASSWORD_LENGTH) return PasswordIssue.TOO_LONG
        if (password.none { it.isLetter() }) return PasswordIssue.MISSING_LETTER
        if (password.none { it in '0'..'9' }) return PasswordIssue.MISSING_DIGIT
        if (password.lowercase(Locale.ROOT) in COMMON) return PasswordIssue.TOO_COMMON
        val local = identifier.substringBefore('@').lowercase(Locale.ROOT)
        if (local.length >= 3 && local in password.lowercase(Locale.ROOT)) return PasswordIssue.CONTAINS_IDENTIFIER
        return null
    }
}

/** One-time code lifetime, attempt budget and resend limits. */
object OtpPolicy {
    const val LENGTH = 6
    const val TTL_MS = 5 * 60_000L
    const val MAX_ATTEMPTS = 3
    const val RESEND_COOLDOWN_MS = 60_000L
    const val MAX_SENDS_PER_HOUR = 5

    fun expiresAt(now: Long): Long = now + TTL_MS
    fun resendAvailableAt(now: Long): Long = now + RESEND_COOLDOWN_MS
    fun isExpired(expiresAt: Long, now: Long): Boolean = now >= expiresAt

    /** Uniformly distributed six-digit code, zero-padded so leading zeros survive. */
    fun generate(random: SecureRandom = SecureRandom()): String =
        random.nextInt(1_000_000).toString().padStart(LENGTH, '0')

    fun isWellFormed(code: String): Boolean =
        code.length == LENGTH && code.all { it in '0'..'9' }
}

/** Escalating lockout after repeated wrong passwords. Persisted so a restart cannot clear it. */
object LockoutPolicy {
    const val MAX_ATTEMPTS = 5
    private const val BASE_MS = 60_000L
    private const val CAP_MS = 5 * 60_000L

    fun lockDurationMs(failedAttempts: Int): Long {
        if (failedAttempts < MAX_ATTEMPTS) return 0
        val steps = failedAttempts - MAX_ATTEMPTS
        val doubled = BASE_MS shl steps.coerceIn(0, 8)
        return doubled.coerceAtMost(CAP_MS)
    }

    fun isLocked(lockUntil: Long?, now: Long): Boolean = lockUntil != null && now < lockUntil
}

/** How long a verified session stays valid before the user must sign in again. */
object SessionPolicy {
    const val LIFETIME_MS = 30L * 24 * 60 * 60 * 1000
    fun create(accountId: String, token: String, now: Long): Session =
        Session(accountId, token, now, now + LIFETIME_MS)
}
