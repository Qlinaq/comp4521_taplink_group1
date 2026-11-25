package com.taplink.app.data

import android.content.Context
import androidx.room.Room
import com.taplink.app.data.auth.RoomAuthRepository
import com.taplink.app.data.auth.SharedPreferencesSessionStore
import com.taplink.app.domain.TapLinkRepository
import com.taplink.app.domain.WalletOwner

/**
 * One database instance is shared by the wallet repository and the account repository so a future
 * scope change (cards owned by an account) can be expressed in a single transaction.
 */
object TapLinkContainer {
    @Volatile private var databaseInstance: TapLinkDatabase? = null
    @Volatile private var instance: TapLinkRepository? = null
    @Volatile private var authInstance: RoomAuthRepository? = null

    fun database(context: Context): TapLinkDatabase = databaseInstance ?: synchronized(this) {
        databaseInstance ?: Room.databaseBuilder(
            context.applicationContext,
            TapLinkDatabase::class.java,
            "taplink.db"
        ).addMigrations(
            TapLinkDatabase.MIGRATION_1_2,
            TapLinkDatabase.MIGRATION_2_3,
            TapLinkDatabase.MIGRATION_3_4,
            TapLinkDatabase.MIGRATION_4_5,
            TapLinkDatabase.MIGRATION_5_6,
            TapLinkDatabase.MIGRATION_6_7
        ).build()
            .also { databaseInstance = it }
    }

    fun repository(context: Context): TapLinkRepository = instance ?: synchronized(this) {
        instance ?: RoomTapLinkRepository(database(context)).also { instance = it }
    }

    /**
     * Whose wallet non-UI work should touch. A worker can run in a fresh process before the wallet
     * shell exists, so it resolves the owner from the stored session instead of assuming the demo
     * wallet.
     */
    fun activeOwner(context: Context): String =
        authRepository(context).session.value?.accountId ?: WalletOwner.GUEST

    fun authRepository(context: Context): RoomAuthRepository = authInstance ?: synchronized(this) {
        authInstance ?: RoomAuthRepository(
            database(context),
            SharedPreferencesSessionStore(context)
        ).also { authInstance = it }
    }
}
