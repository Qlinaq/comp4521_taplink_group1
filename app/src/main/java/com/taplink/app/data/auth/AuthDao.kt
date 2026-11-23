package com.taplink.app.data.auth

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface AuthDao {
    @Query("SELECT * FROM accounts WHERE email = :identifier OR phone = :identifier LIMIT 1")
    suspend fun accountByIdentifier(identifier: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun account(id: String): AccountEntity?

    @Query("SELECT * FROM auth_codes WHERE identifier = :identifier AND purpose = :purpose AND consumedAt IS NULL ORDER BY createdAt DESC LIMIT 1")
    suspend fun activeCode(identifier: String, purpose: String): AuthCodeEntity?

    @Query("SELECT COUNT(*) FROM auth_codes WHERE identifier = :identifier AND createdAt >= :since")
    suspend fun codesSentSince(identifier: String, since: Long): Int

    @Insert
    suspend fun insertAccount(account: AccountEntity)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccount(id: String)

    @Insert
    suspend fun insertCode(code: AuthCodeEntity)

    @Update
    suspend fun updateCode(code: AuthCodeEntity)

    @Query("UPDATE auth_codes SET consumedAt = :now WHERE identifier = :identifier AND purpose = :purpose AND consumedAt IS NULL")
    suspend fun consumeCodes(identifier: String, purpose: String, now: Long)

    @Query("DELETE FROM auth_codes WHERE identifier = :identifier")
    suspend fun deleteCodesFor(identifier: String)
}
