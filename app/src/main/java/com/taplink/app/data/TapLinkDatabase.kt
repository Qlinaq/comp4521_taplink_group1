package com.taplink.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.taplink.app.data.auth.AccountEntity
import com.taplink.app.data.auth.AuthCodeEntity
import com.taplink.app.data.auth.AuthDao

/**
 * Version 2 adds the account tables only.
 * Version 3 adds the interface-language column with a default, so existing preferences rows keep a
 * valid value without a table rebuild.
 * Version 4 adds notification categories, accessibility preferences and the structured message
 * identity used to render localised notification text.
 * Version 5 gives every wallet row an owner, so the demo wallet and each account keep separate
 * cards, balances, history and messages in one database file.
 * Version 6 separates per-wallet offer state from the shared demo catalogue.
 * Version 7 lets a redeemed coupon be spent: the catalogue gains its discount rule, the per-wallet
 * offer state records when it was used, and a payment records the coupon and the money it saved.
 * All migrations are additive; the wallet domain models are unchanged.
 */
@Database(
    entities = [CardEntity::class, MerchantEntity::class, TransactionEntity::class,
        PreferencesEntity::class, MessageEntity::class, LocationEntity::class,
        PassEntity::class, OfferEntity::class, OfferStateEntity::class, SubsidyClaimEntity::class,
        OperationEntity::class, MetaEntity::class, AccountEntity::class, AuthCodeEntity::class],
    version = 7, exportSchema = true
)
abstract class TapLinkDatabase : RoomDatabase() {
    abstract fun dao(): TapLinkDao
    abstract fun authDao(): AuthDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `accounts` (`id` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
                        "`email` TEXT, `phone` TEXT, `emailVerified` INTEGER NOT NULL, `phoneVerified` INTEGER NOT NULL, " +
                        "`passwordSalt` TEXT NOT NULL, `passwordHash` TEXT NOT NULL, `passwordIterations` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `failedAttempts` INTEGER NOT NULL, " +
                        "`lockUntil` INTEGER, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `auth_codes` (`id` TEXT NOT NULL, `identifier` TEXT NOT NULL, " +
                        "`channel` TEXT NOT NULL, `purpose` TEXT NOT NULL, `codeSalt` TEXT NOT NULL, " +
                        "`codeDigest` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `expiresAt` INTEGER NOT NULL, " +
                        "`attemptsLeft` INTEGER NOT NULL, `consumedAt` INTEGER, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_accounts_email` ON `accounts` (`email`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_accounts_phone` ON `accounts` (`phone`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_auth_codes_identifier_purpose` ON `auth_codes` (`identifier`, `purpose`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_auth_codes_createdAt` ON `auth_codes` (`createdAt`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // The default keeps every existing row valid; the language only affects rendering.
                db.execSQL("ALTER TABLE `preferences` ADD COLUMN `language` TEXT NOT NULL DEFAULT 'ENGLISH'")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Notification categories default to on, matching the previous behaviour where
                // reminders were delivered unless explicitly switched off.
                listOf("notificationsEnabled", "transactionAlerts", "offerAlerts", "serviceAnnouncements")
                    .forEach { db.execSQL("ALTER TABLE `preferences` ADD COLUMN `$it` INTEGER NOT NULL DEFAULT 1") }
                // Accessibility options default to off so an update never changes the layout that an
                // existing user already learned.
                listOf("elderlyMode", "largeText", "highContrast", "voiceReading")
                    .forEach { db.execSQL("ALTER TABLE `preferences` ADD COLUMN `$it` INTEGER NOT NULL DEFAULT 0") }
                // Nullable: rows written before this version keep their stored English text.
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `kind` TEXT")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `amountCents` INTEGER")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Previously shared state has no recorded owner. Keep it on disk without
                // attaching it to any wallet. Each wallet starts its own offer history.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS offer_states (" +
                        "ownerId TEXT NOT NULL, offerId TEXT NOT NULL, isFavorite INTEGER NOT NULL, " +
                        "redeemedAt INTEGER, couponCode TEXT, PRIMARY KEY(ownerId, offerId), " +
                        "FOREIGN KEY(offerId) REFERENCES offers(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_offer_states_offerId ON offer_states (offerId)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Catalogue rule: what a coupon is worth. Defaults describe "no discount", which is
                // exactly what the three rows written by version 6 were, so no row becomes wrong.
                db.execSQL("ALTER TABLE `offers` ADD COLUMN `discountKind` TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE `offers` ADD COLUMN `discountValue` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `offers` ADD COLUMN `minSpendCents` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `offers` ADD COLUMN `maxDiscountCents` INTEGER NOT NULL DEFAULT 0")
                // Mirrors of the per-wallet columns; legacy rows keep their historical values.
                db.execSQL("ALTER TABLE `offers` ADD COLUMN `usedAt` INTEGER")
                db.execSQL("ALTER TABLE `offers` ADD COLUMN `usedTransactionId` TEXT")
                // Per wallet: when this coupon was spent. NULL means it is still available.
                db.execSQL("ALTER TABLE `offer_states` ADD COLUMN `usedAt` INTEGER")
                db.execSQL("ALTER TABLE `offer_states` ADD COLUMN `usedTransactionId` TEXT")
                // A payment now records the coupon and the money it took off the merchant price.
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `discountCents` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `couponId` TEXT")
                // Existing installs keep their old catalogue rows; the repository refreshes the
                // discount rule and expiry for the known demo coupons on the next launch.
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Existing rows are the wallet of whoever was using this installation before accounts
                // had their own; they are marked as the demo wallet. The repository adopts them into a
                // signed-in account once, on the first launch after this upgrade.
                listOf("cards", "transactions", "messages", "passes", "subsidy_claims", "operations")
                    .forEach { db.execSQL("ALTER TABLE `$it` ADD COLUMN `ownerId` TEXT NOT NULL DEFAULT 'guest'") }
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_ownerId` ON `cards` (`ownerId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_ownerId` ON `transactions` (`ownerId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_ownerId` ON `messages` (`ownerId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_passes_ownerId` ON `passes` (`ownerId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subsidy_claims_ownerId` ON `subsidy_claims` (`ownerId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `app_meta` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")
            }
        }
    }
}
