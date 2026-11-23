package com.taplink.app.data.auth

import androidx.room.withTransaction
import com.taplink.app.data.TapLinkDatabase
import com.taplink.app.domain.auth.Account
import com.taplink.app.domain.auth.AuthChannel
import com.taplink.app.domain.auth.AuthError
import com.taplink.app.domain.auth.AuthResult
import com.taplink.app.domain.auth.AuthRules
import com.taplink.app.domain.auth.CodeDelivery
import com.taplink.app.domain.auth.CodeHasher
import com.taplink.app.domain.auth.LockoutPolicy
import com.taplink.app.domain.auth.OtpPolicy
import com.taplink.app.domain.auth.OtpPurpose
import com.taplink.app.domain.auth.PasswordHasher
import com.taplink.app.domain.auth.Pbkdf2PasswordHasher
import com.taplink.app.domain.auth.Session
import com.taplink.app.domain.auth.SessionPolicy
import com.taplink.app.domain.auth.SimulatedVerificationSender
import com.taplink.app.domain.auth.TokenFactory
import com.taplink.app.domain.auth.VerificationSender
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Local-first account store. Every decision that protects money-adjacent state lives here rather
 * than in the UI: password stretching, one-time code lifetime and attempt budget, lockout
 * escalation and session creation.
 *
 * The single-profile scope of this prototype is deliberate and documented: signing in gates access,
 * it does not partition the wallet between accounts.
 */
class RoomAuthRepository(
    private val db: TapLinkDatabase,
    private val storage: SessionStorage,
    private val sender: VerificationSender = SimulatedVerificationSender(),
    private val hasher: PasswordHasher = Pbkdf2PasswordHasher(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val newToken: () -> String = { TokenFactory.newToken() }
) {
    private val dao = db.authDao()

    private val _session = MutableStateFlow(storage.read())
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val _guest = MutableStateFlow(storage.readGuest())
    val guest: StateFlow<Boolean> = _guest.asStateFlow()

    /** Latest code, surfaced by the demo inbox. Empty when the transport is not simulated. */
    val demoDelivery: StateFlow<CodeDelivery?> =
        (sender as? SimulatedVerificationSender)?.lastDelivery ?: MutableStateFlow(null)

    val simulatedDelivery: Boolean get() = sender.simulated

    suspend fun startSignUp(displayName: String, rawIdentifier: String, password: String): AuthResult<Unit> = safely signUp@{
        val (channel, identifier) = AuthRules.normalizeIdentifier(rawIdentifier)
            ?: return@signUp AuthResult.Failure(AuthError.INVALID_IDENTIFIER)
        val name = displayName.trim()
        if (name.isEmpty() || name.length > 60) return@signUp AuthResult.Failure(AuthError.INVALID_NAME)
        AuthRules.passwordProblem(password, identifier)
            ?.let { return@signUp AuthResult.Failure(AuthError.INVALID_PASSWORD) }

        val now = clock()
        val existing = dao.accountByIdentifier(identifier)
        if (existing != null && isVerified(existing, channel)) return@signUp AuthResult.Failure(AuthError.IDENTIFIER_TAKEN)
        if (LockoutPolicy.isLocked(existing?.lockUntil, now)) return@signUp AuthResult.Failure(AuthError.ACCOUNT_LOCKED)

        val stored = hasher.hash(password)
        if (existing == null) {
            dao.insertAccount(
                AccountEntity(
                    id = newId(),
                    displayName = name,
                    email = identifier.takeIf { channel == AuthChannel.EMAIL },
                    phone = identifier.takeIf { channel == AuthChannel.PHONE },
                    emailVerified = false,
                    phoneVerified = false,
                    passwordSalt = stored.salt,
                    passwordHash = stored.hash,
                    passwordIterations = stored.iterations,
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            // An abandoned sign-up can be restarted: the unverified account is updated in place so
            // the identifier does not become permanently unusable.
            dao.updateAccount(
                existing.copy(
                    displayName = name,
                    passwordSalt = stored.salt,
                    passwordHash = stored.hash,
                    passwordIterations = stored.iterations,
                    failedAttempts = 0,
                    lockUntil = null,
                    updatedAt = now
                )
            )
        }
        issueCode(identifier, channel, OtpPurpose.SIGNUP)
    }

    suspend fun confirmSignUp(rawIdentifier: String, code: String): AuthResult<Session> = safely confirm@{
        val (channel, identifier) = AuthRules.normalizeIdentifier(rawIdentifier)
            ?: return@confirm AuthResult.Failure(AuthError.INVALID_IDENTIFIER)
        db.withTransaction {
            val account = dao.accountByIdentifier(identifier)
                ?: return@withTransaction AuthResult.Failure(AuthError.ACCOUNT_NOT_FOUND)
            val consumed = consumeCode(identifier, OtpPurpose.SIGNUP, code)
            if (consumed is AuthResult.Failure) return@withTransaction consumed
            val verified = if (channel == AuthChannel.EMAIL) account.copy(emailVerified = true)
                else account.copy(phoneVerified = true)
            dao.updateAccount(verified.copy(failedAttempts = 0, lockUntil = null, updatedAt = clock()))
            val session = SessionPolicy.create(account.id, newToken(), clock())
            storage.write(session)
            _session.value = session
            AuthResult.Success(session)
        }
    }

    suspend fun signIn(rawIdentifier: String, password: String): AuthResult<Session> = safely signIn@{
        val (channel, identifier) = AuthRules.normalizeIdentifier(rawIdentifier)
            ?: return@signIn AuthResult.Failure(AuthError.WRONG_CREDENTIALS)
        val now = clock()
        // An unknown identifier and a wrong password share one outcome so the screen cannot be used
        // to enumerate registered addresses.
        val account = dao.accountByIdentifier(identifier)
            ?: return@signIn AuthResult.Failure(AuthError.WRONG_CREDENTIALS)
        if (LockoutPolicy.isLocked(account.lockUntil, now)) return@signIn AuthResult.Failure(AuthError.ACCOUNT_LOCKED)

        if (!hasher.verify(password, account.passwordSalt, account.passwordHash, account.passwordIterations)) {
            val attempts = account.failedAttempts + 1
            val lockFor = LockoutPolicy.lockDurationMs(attempts)
            dao.updateAccount(
                account.copy(
                    failedAttempts = attempts,
                    lockUntil = if (lockFor > 0) now + lockFor else account.lockUntil,
                    updatedAt = now
                )
            )
            val error = if (lockFor > 0) AuthError.ACCOUNT_LOCKED else AuthError.WRONG_CREDENTIALS
            return@signIn AuthResult.Failure(error)
        }

        if (!isVerified(account, channel)) {
            dao.updateAccount(account.copy(failedAttempts = 0, lockUntil = null, updatedAt = now))
            return@signIn AuthResult.Failure(AuthError.NOT_VERIFIED)
        }
        if (account.failedAttempts != 0 || account.lockUntil != null) {
            dao.updateAccount(account.copy(failedAttempts = 0, lockUntil = null, updatedAt = now))
        }
        val session = SessionPolicy.create(account.id, newToken(), now)
        storage.write(session)
        _session.value = session
        AuthResult.Success(session)
    }

    /**
     * Issues a fresh sign-up code without touching the stored password, so a resend never requires
     * the password to be held in memory a second time.
     */
    suspend fun resendSignUpCode(rawIdentifier: String): AuthResult<Unit> = safely resend@{
        val (channel, identifier) = AuthRules.normalizeIdentifier(rawIdentifier)
            ?: return@resend AuthResult.Failure(AuthError.INVALID_IDENTIFIER)
        val account = dao.accountByIdentifier(identifier)
            ?: return@resend AuthResult.Failure(AuthError.ACCOUNT_NOT_FOUND)
        if (isVerified(account, channel)) return@resend AuthResult.Failure(AuthError.IDENTIFIER_TAKEN)
        issueCode(identifier, channel, OtpPurpose.SIGNUP)
    }

    suspend fun requestReset(rawIdentifier: String): AuthResult<Unit> = safely reset@{        val (channel, identifier) = AuthRules.normalizeIdentifier(rawIdentifier)
            ?: return@reset AuthResult.Failure(AuthError.INVALID_IDENTIFIER)
        val account = dao.accountByIdentifier(identifier)
            ?: return@reset AuthResult.Failure(AuthError.ACCOUNT_NOT_FOUND)
        if (!isVerified(account, channel)) return@reset AuthResult.Failure(AuthError.NOT_VERIFIED)
        issueCode(identifier, channel, OtpPurpose.RESET)
    }

    suspend fun confirmReset(rawIdentifier: String, code: String, newPassword: String): AuthResult<Unit> = safely confirmReset@{
        val (_, identifier) = AuthRules.normalizeIdentifier(rawIdentifier)
            ?: return@confirmReset AuthResult.Failure(AuthError.INVALID_IDENTIFIER)
        val account = dao.accountByIdentifier(identifier)
            ?: return@confirmReset AuthResult.Failure(AuthError.ACCOUNT_NOT_FOUND)
        AuthRules.passwordProblem(newPassword, identifier)
            ?.let { return@confirmReset AuthResult.Failure(AuthError.INVALID_PASSWORD) }
        if (hasher.verify(newPassword, account.passwordSalt, account.passwordHash, account.passwordIterations)) {
            return@confirmReset AuthResult.Failure(AuthError.INVALID_PASSWORD)
        }
        db.withTransaction {
            val consumed = consumeCode(identifier, OtpPurpose.RESET, code)
            if (consumed is AuthResult.Failure) return@withTransaction consumed
            val stored = hasher.hash(newPassword)
            dao.updateAccount(
                account.copy(
                    passwordSalt = stored.salt,
                    passwordHash = stored.hash,
                    passwordIterations = stored.iterations,
                    failedAttempts = 0,
                    lockUntil = null,
                    updatedAt = clock()
                )
            )
            // A reset invalidates the current session: the new password must be used to sign in.
            signOut()
            AuthResult.Success(Unit)
        }
    }

    suspend fun changePassword(accountId: String, currentPassword: String, newPassword: String): AuthResult<Unit> = safely change@{
        val account = dao.account(accountId) ?: return@change AuthResult.Failure(AuthError.ACCOUNT_NOT_FOUND)
        if (!hasher.verify(currentPassword, account.passwordSalt, account.passwordHash, account.passwordIterations)) {
            return@change AuthResult.Failure(AuthError.WRONG_CREDENTIALS)
        }
        val identifier = account.email ?: account.phone.orEmpty()
        if (currentPassword == newPassword) return@change AuthResult.Failure(AuthError.INVALID_PASSWORD)
        AuthRules.passwordProblem(newPassword, identifier)
            ?.let { return@change AuthResult.Failure(AuthError.INVALID_PASSWORD) }
        val stored = hasher.hash(newPassword)
        dao.updateAccount(
            account.copy(
                passwordSalt = stored.salt,
                passwordHash = stored.hash,
                passwordIterations = stored.iterations,
                updatedAt = clock()
            )
        )
        AuthResult.Success(Unit)
    }

    suspend fun deleteAccount(accountId: String, password: String): AuthResult<Unit> = safely delete@{
        val account = dao.account(accountId) ?: return@delete AuthResult.Failure(AuthError.ACCOUNT_NOT_FOUND)
        if (!hasher.verify(password, account.passwordSalt, account.passwordHash, account.passwordIterations)) {
            return@delete AuthResult.Failure(AuthError.WRONG_CREDENTIALS)
        }
        db.withTransaction {
            dao.deleteCodesFor(account.email ?: account.phone.orEmpty())
            dao.deleteAccount(accountId)
            signOut()
        }
        AuthResult.Success(Unit)
    }

    suspend fun currentAccount(): Account? {
        val session = _session.value ?: return null
        val entity = runCatching { dao.account(session.accountId) }.getOrNull()
        if (entity == null) {
            signOut()
            return null
        }
        return entity.toDomain()
    }

    fun signOut() {
        storage.clear()
        _session.value = null
        _guest.value = false
    }

    /**
     * Explicit, persisted choice to use the wallet without an account. Kept so a demonstration can
     * never be blocked by the sign-in wall.
     */
    fun continueAsGuest() {
        storage.clear()
        storage.writeGuest(true)
        _session.value = null
        _guest.value = true
    }

    /** Leaves the demo mode so the sign-in or sign-up screen can take over. */
    fun leaveGuest() {
        storage.writeGuest(false)
        _guest.value = false
    }

    /**
     * Issues a code, enforcing the resend cooldown and hourly cap. Any earlier live code for the
     * same identifier and purpose is invalidated first, so only the newest code can succeed.
     */
    private suspend fun issueCode(identifier: String, channel: AuthChannel, purpose: OtpPurpose): AuthResult<Unit> {
        val now = clock()
        if (dao.codesSentSince(identifier, now - 60 * 60_000L) >= OtpPolicy.MAX_SENDS_PER_HOUR) {
            return AuthResult.Failure(AuthError.RESEND_TOO_SOON)
        }
        val active = dao.activeCode(identifier, purpose.name)
        if (active != null && now < active.createdAt + OtpPolicy.RESEND_COOLDOWN_MS) {
            return AuthResult.Failure(AuthError.RESEND_TOO_SOON)
        }
        val code = OtpPolicy.generate()
        val salt = CodeHasher.newSalt()
        dao.consumeCodes(identifier, purpose.name, now)
        dao.insertCode(
            AuthCodeEntity(
                id = newId(),
                identifier = identifier,
                channel = channel.name,
                purpose = purpose.name,
                codeSalt = salt,
                codeDigest = CodeHasher.digest(identifier, code, salt),
                createdAt = now,
                expiresAt = OtpPolicy.expiresAt(now),
                attemptsLeft = OtpPolicy.MAX_ATTEMPTS
            )
        )
        val delivery = CodeDelivery(identifier, channel, purpose, code, OtpPolicy.expiresAt(now), sender.simulated)
        return when (val sent = sender.send(delivery)) {
            is AuthResult.Success -> AuthResult.Success(Unit)
            is AuthResult.Failure -> sent
        }
    }

    /** Consumes the active code or spends one attempt. A wrong code is a value, never an exception. */
    private suspend fun consumeCode(identifier: String, purpose: OtpPurpose, code: String): AuthResult<Unit> {
        val now = clock()
        val active = dao.activeCode(identifier, purpose.name)
            ?: return AuthResult.Failure(AuthError.CODE_EXPIRED)
        if (OtpPolicy.isExpired(active.expiresAt, now)) {
            dao.updateCode(active.copy(consumedAt = now))
            return AuthResult.Failure(AuthError.CODE_EXPIRED)
        }
        if (active.attemptsLeft <= 0) return AuthResult.Failure(AuthError.TOO_MANY_ATTEMPTS)
        if (!OtpPolicy.isWellFormed(code)) return AuthResult.Failure(AuthError.CODE_INVALID)
        if (!CodeHasher.matches(identifier, code, active.codeSalt, active.codeDigest)) {
            val remaining = active.attemptsLeft - 1
            dao.updateCode(active.copy(attemptsLeft = remaining))
            return AuthResult.Failure(if (remaining <= 0) AuthError.TOO_MANY_ATTEMPTS else AuthError.CODE_INVALID)
        }
        dao.updateCode(active.copy(consumedAt = now))
        return AuthResult.Success(Unit)
    }

    private fun isVerified(account: AccountEntity, channel: AuthChannel): Boolean =
        if (channel == AuthChannel.EMAIL) account.emailVerified else account.phoneVerified

    private fun AccountEntity.toDomain(): Account = Account(
        id = id,
        displayName = displayName,
        email = email,
        phone = phone,
        emailVerified = emailVerified,
        phoneVerified = phoneVerified,
        createdAt = createdAt,
        failedAttempts = failedAttempts,
        lockUntil = lockUntil
    )

    private suspend fun <T> safely(block: suspend () -> AuthResult<T>): AuthResult<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        AuthResult.Failure(AuthError.STORAGE_ERROR)
    }
}
