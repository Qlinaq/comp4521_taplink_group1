package com.taplink.app.data.auth

import android.content.Context
import com.taplink.app.domain.auth.Session

/** Persistence seam for the session, so the auth logic stays testable without a Context. */
interface SessionStorage {
    fun read(): Session?
    fun write(session: Session)
    fun clear()

    /** True while the user has explicitly chosen the no-account demo mode. */
    fun readGuest(): Boolean
    fun writeGuest(value: Boolean)
}

/**
 * The session is a random opaque token plus an expiry. Credentials are never written here, and the
 * file lives in the app's private directory. A production build would wrap the token with a
 * Keystore-backed key; that upgrade is documented in docs/AUTH_DESIGN.md rather than implied.
 */
class SharedPreferencesSessionStore(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis
) : SessionStorage {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun read(): Session? {
        val accountId = prefs.getString(KEY_ACCOUNT, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val issuedAt = prefs.getLong(KEY_ISSUED, 0L)
        val expiresAt = prefs.getLong(KEY_EXPIRES, 0L)
        val session = Session(accountId, token, issuedAt, expiresAt)
        if (session.isExpired(clock())) {
            clear()
            return null
        }
        return session
    }

    override fun write(session: Session) {
        prefs.edit()
            .putString(KEY_ACCOUNT, session.accountId)
            .putString(KEY_TOKEN, session.token)
            .putLong(KEY_ISSUED, session.issuedAt)
            .putLong(KEY_EXPIRES, session.expiresAt)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    override fun readGuest(): Boolean = prefs.getBoolean(KEY_GUEST, false)

    override fun writeGuest(value: Boolean) {
        prefs.edit().putBoolean(KEY_GUEST, value).apply()
    }

    private companion object {
        const val FILE = "taplink_session"
        const val KEY_ACCOUNT = "accountId"
        const val KEY_TOKEN = "token"
        const val KEY_ISSUED = "issuedAt"
        const val KEY_EXPIRES = "expiresAt"
        const val KEY_GUEST = "guest"
    }
}

/** Used by tests and previews. Enforces the same expiry rule as the real store. */
class InMemorySessionStorage(private val clock: () -> Long = System::currentTimeMillis) : SessionStorage {
    private var session: Session? = null
    private var guest = false

    override fun read(): Session? {
        val current = session ?: return null
        if (current.isExpired(clock())) {
            session = null
            return null
        }
        return current
    }

    override fun write(session: Session) {
        this.session = session
        guest = false
    }

    override fun clear() {
        session = null
        guest = false
    }

    override fun readGuest(): Boolean = guest

    override fun writeGuest(value: Boolean) {
        guest = value
    }
}
