package com.taplink.app.domain.auth

/** How an account receives a one-time code. */
enum class AuthChannel { EMAIL, PHONE }

/** Why a one-time code was issued. Codes are never reused across purposes. */
enum class OtpPurpose { SIGNUP, RESET }

/** Machine-readable outcomes. User-facing wording is decided by the UI layer so it can be localised. */
enum class AuthError {
    INVALID_IDENTIFIER,
    INVALID_NAME,
    IDENTIFIER_TAKEN,
    INVALID_PASSWORD,
    WRONG_CREDENTIALS,
    ACCOUNT_LOCKED,
    ACCOUNT_NOT_FOUND,
    NOT_VERIFIED,
    CODE_INVALID,
    CODE_EXPIRED,
    CODE_ALREADY_USED,
    TOO_MANY_ATTEMPTS,
    RESEND_TOO_SOON,
    SESSION_EXPIRED,
    STORAGE_ERROR
}

/** A stored account. The password is never held in this type. */
data class Account(
    val id: String,
    val displayName: String,
    val email: String?,
    val phone: String?,
    val emailVerified: Boolean,
    val phoneVerified: Boolean,
    val createdAt: Long,
    val failedAttempts: Int = 0,
    val lockUntil: Long? = null
) {
    val primaryIdentifier: String get() = email ?: phone.orEmpty()
    val isLocked: Boolean get() = lockUntil != null
}

sealed interface AuthResult<out T> {
    data class Success<T>(val value: T) : AuthResult<T>
    data class Failure(val code: AuthError) : AuthResult<Nothing>
}

/** In-memory description of a code that was just issued, used by the demo inbox. */
data class CodeDelivery(
    val identifier: String,
    val channel: AuthChannel,
    val purpose: OtpPurpose,
    val code: String,
    val expiresAt: Long,
    val simulated: Boolean
)

/** A verified session. Only the token is persisted; credentials never are. */
data class Session(val accountId: String, val token: String, val issuedAt: Long, val expiresAt: Long) {
    fun isExpired(now: Long): Boolean = now >= expiresAt
}
