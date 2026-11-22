package com.taplink.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CardEntity::class, MerchantEntity::class, TransactionEntity::class,
        PreferencesEntity::class, MessageEntity::class, LocationEntity::class,
        PassEntity::class, OfferEntity::class, SubsidyClaimEntity::class, OperationEntity::class],
    version = 1, exportSchema = true
)
abstract class TapLinkDatabase : RoomDatabase() {
    abstract fun dao(): TapLinkDao
}
