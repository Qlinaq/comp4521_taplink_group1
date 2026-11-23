package com.taplink.app.data.auth

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A registered account. Only a salted PBKDF2 digest is stored; the password itself never reaches
 * the database, a log line, or a saved instance state.
 *
 * Nullable unique indices rely on SQLite treating NULLs as distinct, so an account with only an
 * email and one with only a phone number can coexist.
 */
@Entity(
    tableName = "accounts",
    indices = [Index(value = ["email"], unique = true), Index(value = ["phone"], unique = true)]
)
data class AccountEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val email: String?,
    val phone: String?,
    val emailVerified: Boolean,
    val phoneVerified: Boolean,
    val passwordSalt: String,
    val passwordHash: String,
    val passwordIterations: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val failedAttempts: Int = 0,
    val lockUntil: Long? = null
)

/**
 * A one-time code. The code is stored as a salted SHA-256 digest so a dumped database does not hand
 * an attacker a live reset code. Rows are consumed rather than deleted to keep an audit trail of
 * how many codes a device has requested.
 */
@Entity(
    tableName = "auth_codes",
    indices = [Index(value = ["identifier", "purpose"]), Index("createdAt")]
)
data class AuthCodeEntity(
    @PrimaryKey val id: String,
    val identifier: String,
    val channel: String,
    val purpose: String,
    val codeSalt: String,
    val codeDigest: String,
    val createdAt: Long,
    val expiresAt: Long,
    val attemptsLeft: Int,
    val consumedAt: Long? = null
)
