package com.taplink.app.data.auth

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.taplink.app.data.TapLinkDatabase
import com.taplink.app.domain.auth.AuthError
import com.taplink.app.domain.auth.AuthResult
import com.taplink.app.domain.auth.AuthRules
import com.taplink.app.domain.auth.OtpPolicy
import com.taplink.app.domain.auth.Pbkdf2PasswordHasher
import com.taplink.app.domain.auth.SimulatedVerificationSender
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomAuthRepositoryTest {

    private lateinit var database: TapLinkDatabase
    private lateinit var storage: InMemorySessionStorage
    private lateinit var sender: SimulatedVerificationSender
    private var now = 1_700_000_000_000L

    private val email = "ziyao@ust.hk"
    private val password = "Maple-River-42"

    private fun repository() = RoomAuthRepository(
        db = database,
        storage = storage,
        sender = sender,
        hasher = Pbkdf2PasswordHasher(iterations = 1_000),
        clock = { now }
    )

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TapLinkDatabase::class.java
        ).allowMainThreadQueries().build()
        storage = InMemorySessionStorage { now }
        sender = SimulatedVerificationSender()
    }

    @After
    fun tearDown() = database.close()

    private suspend fun signUpAndConfirm(repo: RoomAuthRepository, identifier: String = email): AuthResult<*> {
        val started = repo.startSignUp("Ziyao Wu", identifier, password)
        assertTrue("start sign-up failed: $started", started is AuthResult.Success)
        val code = sender.lastDelivery.value?.code
        assertNotNull("no code was delivered", code)
        return repo.confirmSignUp(identifier, code!!)
    }

    @Test
    fun signUpStoresOnlyASaltedDigest() = runBlocking {
        val repo = repository()
        assertTrue(repo.startSignUp("Ziyao Wu", email, password) is AuthResult.Success)

        val row = database.authDao().accountByIdentifier(email)!!
        assertFalse("password must not appear in the hash column", row.passwordHash.contains(password))
        assertFalse("password must not appear in the salt column", row.passwordSalt.contains(password))
        assertTrue(row.passwordIterations >= 1_000)
        assertTrue("hash should be a base64 digest", row.passwordHash.length >= 32)
        assertFalse("a new account starts unverified", row.emailVerified)
    }

    @Test
    fun plaintextPasswordIsAbsentFromEveryColumn() = runBlocking {
        val repo = repository()
        repo.startSignUp("Ziyao Wu", email, password)

        database.openHelper.readableDatabase.query("SELECT * FROM accounts").use { cursor ->
            while (cursor.moveToNext()) {
                for (index in 0 until cursor.columnCount) {
                    val value = if (cursor.getType(index) == android.database.Cursor.FIELD_TYPE_STRING)
                        cursor.getString(index) else null
                    assertFalse("found plaintext password in column $index", value == password)
                }
            }
        }
    }

    @Test
    fun confirmingTheCodeCreatesAVerifiedSession() = runBlocking {
        val repo = repository()
        val result = signUpAndConfirm(repo)

        assertTrue("confirm failed: $result", result is AuthResult.Success)
        assertTrue(database.authDao().accountByIdentifier(email)!!.emailVerified)
        assertNotNull(repo.session.value)
        assertNotNull(storage.read())
    }

    @Test
    fun signingInWithTheWrongPasswordFails() = runBlocking {
        val repo = repository()
        signUpAndConfirm(repo)
        repo.signOut()

        val result = repo.signIn(email, "Wrong-Password-7")
        assertEquals(AuthResult.Failure(AuthError.WRONG_CREDENTIALS), result)
        assertNull(repo.session.value)
        assertEquals(1, database.authDao().accountByIdentifier(email)!!.failedAttempts)
    }

    @Test
    fun signingInBeforeVerificationIsRejected() = runBlocking {
        val repo = repository()
        repo.startSignUp("Ziyao Wu", email, password)

        assertEquals(AuthResult.Failure(AuthError.NOT_VERIFIED), repo.signIn(email, password))
    }

    @Test
    fun fiveWrongPasswordsLockTheAccount() = runBlocking {
        val repo = repository()
        signUpAndConfirm(repo)
        repo.signOut()

        repeat(4) { assertEquals(AuthResult.Failure(AuthError.WRONG_CREDENTIALS), repo.signIn(email, "Wrong-Password-7")) }
        assertEquals(AuthResult.Failure(AuthError.ACCOUNT_LOCKED), repo.signIn(email, "Wrong-Password-7"))

        // The correct password is still refused while the lock is live.
        assertEquals(AuthResult.Failure(AuthError.ACCOUNT_LOCKED), repo.signIn(email, password))
        assertNotNull(database.authDao().accountByIdentifier(email)!!.lockUntil)
    }

    @Test
    fun theLockExpiresOnItsOwn() = runBlocking {
        val repo = repository()
        signUpAndConfirm(repo)
        repo.signOut()
        repeat(5) { repo.signIn(email, "Wrong-Password-7") }

        now += 61_000L
        assertTrue(repo.signIn(email, password) is AuthResult.Success)
        assertEquals(0, database.authDao().accountByIdentifier(email)!!.failedAttempts)
    }

    @Test
    fun aWrongCodeSpendsAnAttempt() = runBlocking {
        val repo = repository()
        repo.startSignUp("Ziyao Wu", email, password)
        val real = sender.lastDelivery.value!!.code
        val wrong = if (real == "000000") "000001" else "000000"

        assertEquals(AuthResult.Failure(AuthError.CODE_INVALID), repo.confirmSignUp(email, wrong))
        assertEquals(AuthResult.Failure(AuthError.CODE_INVALID), repo.confirmSignUp(email, wrong))
        assertEquals(AuthResult.Failure(AuthError.TOO_MANY_ATTEMPTS), repo.confirmSignUp(email, wrong))
        // A burned code cannot be rescued with the correct value afterwards.
        assertEquals(AuthResult.Failure(AuthError.TOO_MANY_ATTEMPTS), repo.confirmSignUp(email, real))
    }

    @Test
    fun anExpiredCodeIsRejected() = runBlocking {
        val repo = repository()
        repo.startSignUp("Ziyao Wu", email, password)
        val code = sender.lastDelivery.value!!.code

        now += OtpPolicy.TTL_MS + 1
        assertEquals(AuthResult.Failure(AuthError.CODE_EXPIRED), repo.confirmSignUp(email, code))
    }

    @Test
    fun resendingImmediatelyIsRefused() = runBlocking {
        val repo = repository()
        repo.startSignUp("Ziyao Wu", email, password)
        assertEquals(AuthResult.Failure(AuthError.RESEND_TOO_SOON), repo.startSignUp("Ziyao Wu", email, password))
    }

    @Test
    fun aResentCodeInvalidatesThePreviousOne() = runBlocking {
        val repo = repository()
        repo.startSignUp("Ziyao Wu", email, password)
        val first = sender.lastDelivery.value!!.code

        now += OtpPolicy.RESEND_COOLDOWN_MS + 1
        repo.startSignUp("Ziyao Wu", email, password)
        val second = sender.lastDelivery.value!!.code

        // Only the newest code is live: the superseded one is rejected even if it is remembered.
        if (first != second) {
            assertEquals(AuthResult.Failure(AuthError.CODE_INVALID), repo.confirmSignUp(email, first))
        }
        assertTrue(repo.confirmSignUp(email, second) is AuthResult.Success)
    }

    @Test
    fun anExistingVerifiedIdentifierCannotSignUpAgain() = runBlocking {
        val repo = repository()
        signUpAndConfirm(repo)
        repo.signOut()
        assertEquals(AuthResult.Failure(AuthError.IDENTIFIER_TAKEN), repo.startSignUp("Someone", email, "Another-Pass-9"))
    }

    @Test
    fun resetReplacesThePasswordAndEndsTheSession() = runBlocking {
        val repo = repository()
        signUpAndConfirm(repo)
        assertNotNull(repo.session.value)

        assertTrue(repo.requestReset(email) is AuthResult.Success)
        val code = sender.lastDelivery.value!!.code
        val newPassword = "Harbour-Light-88"
        assertTrue(repo.confirmReset(email, code, newPassword) is AuthResult.Success)

        assertNull("a reset must end the session", repo.session.value)
        assertEquals(AuthResult.Failure(AuthError.WRONG_CREDENTIALS), repo.signIn(email, password))
        assertTrue(repo.signIn(email, newPassword) is AuthResult.Success)
    }

    @Test
    fun resetRejectsReusingTheSamePassword() = runBlocking {
        val repo = repository()
        signUpAndConfirm(repo)
        repo.requestReset(email)
        val code = sender.lastDelivery.value!!.code

        assertEquals(AuthResult.Failure(AuthError.INVALID_PASSWORD), repo.confirmReset(email, code, password))
    }

    @Test
    fun changingThePasswordRequiresTheCurrentOne() = runBlocking {
        val repo = repository()
        signUpAndConfirm(repo)
        val accountId = repo.session.value!!.accountId

        assertEquals(
            AuthResult.Failure(AuthError.WRONG_CREDENTIALS),
            repo.changePassword(accountId, "Not-My-Pass-1", "Harbour-Light-88")
        )
        assertTrue(repo.changePassword(accountId, password, "Harbour-Light-88") is AuthResult.Success)
        repo.signOut()
        assertTrue(repo.signIn(email, "Harbour-Light-88") is AuthResult.Success)
    }

    @Test
    fun aPhoneAccountIsSupportedEndToEnd() = runBlocking {
        val repo = repository()
        val phone = "+85291234567"
        assertTrue(repo.startSignUp("Ziyao Wu", "9123 4567", password) is AuthResult.Success)
        val code = sender.lastDelivery.value!!.code
        assertTrue(repo.confirmSignUp(phone, code) is AuthResult.Success)

        repo.signOut()
        assertTrue(repo.signIn("9123 4567", password) is AuthResult.Success)
        val account = repo.currentAccount()!!
        assertEquals(phone, account.phone)
        assertEquals("+852 •••• 4567", AuthRules.maskIdentifier(account.phone!!))
    }

    @Test
    fun deletingTheAccountRemovesItAndEndsTheSession() = runBlocking {
        val repo = repository()
        signUpAndConfirm(repo)
        val accountId = repo.session.value!!.accountId

        assertEquals(
            AuthResult.Failure(AuthError.WRONG_CREDENTIALS),
            repo.deleteAccount(accountId, "Not-My-Pass-1")
        )
        assertTrue(repo.deleteAccount(accountId, password) is AuthResult.Success)
        assertNull(repo.session.value)
        assertNull(database.authDao().account(accountId))
    }

    @Test
    fun aRestoredSessionSurvivesUntilItExpires() = runBlocking {
        val repo = repository()
        signUpAndConfirm(repo)
        val restored = repository()
        assertNotNull("session should be reused", restored.session.value)

        now += 31L * 24 * 60 * 60 * 1000
        assertNull(repository().session.value)
    }

    @Test
    fun aWeakPasswordIsRejectedBeforeAnyRowIsWritten() = runBlocking {
        val repo = repository()
        assertEquals(AuthResult.Failure(AuthError.INVALID_PASSWORD), repo.startSignUp("Ziyao", email, "abc"))
        assertNull(database.authDao().accountByIdentifier(email))
    }

    @Test
    fun anInvalidIdentifierIsRejected() = runBlocking {
        val repo = repository()
        assertEquals(AuthResult.Failure(AuthError.INVALID_IDENTIFIER), repo.startSignUp("Ziyao", "not-an-address", password))
        assertEquals(AuthResult.Failure(AuthError.INVALID_IDENTIFIER), repo.startSignUp("Ziyao", "12345", password))
    }
}
