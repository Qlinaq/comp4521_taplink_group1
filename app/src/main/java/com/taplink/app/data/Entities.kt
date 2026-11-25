package com.taplink.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.taplink.app.domain.*

/**
 * Wallet tables carry `ownerId` (see [com.taplink.app.domain.WalletOwner]). The column is not part of
 * the domain model: ownership is a storage concern, and no screen may read another owner's rows.
 * Merchants and offers are a shared demo catalogue, so they carry no owner.
 */
@Entity(tableName = "cards", primaryKeys = ["id"], indices = [Index("ownerId")])
data class CardEntity(@Embedded val value: Card, val ownerId: String = WalletOwner.GUEST)

@Entity(tableName = "merchants", primaryKeys = ["id"])
data class MerchantEntity(@Embedded val value: Merchant)

@Entity(
    tableName = "transactions",
    foreignKeys = [ForeignKey(entity = CardEntity::class, parentColumns = ["id"], childColumns = ["cardId"])],
    indices = [Index(value = ["id"], unique = true), Index("cardId"), Index("createdAt"), Index("ownerId"),
        Index(value = ["operationId", "type"], unique = true)]
)
data class TransactionEntity(
    @Embedded val value: WalletTransaction,
    val ownerId: String = WalletOwner.GUEST,
    // SQLite assigns this in insertion order inside the same transaction as the money operation.
    // Unlike a UUID or wall clock, it preserves debit/reload order when timestamps tie.
    @PrimaryKey(autoGenerate = true) val ledgerSequence: Long = 0
)

@Entity(tableName = "preferences")
data class PreferencesEntity(@PrimaryKey val singletonId: Int = 1, @Embedded val value: AppPreferences)

@Entity(tableName = "messages", primaryKeys = ["id"], indices = [Index("createdAt"), Index("ownerId")])
data class MessageEntity(@Embedded val value: WalletMessage, val ownerId: String = WalletOwner.GUEST)

@Entity(tableName = "locations", primaryKeys = ["id"])
data class LocationEntity(@Embedded val value: ServiceLocation)

@Entity(
    tableName = "passes", primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(entity = CardEntity::class, parentColumns = ["id"], childColumns = ["cardId"]),
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"])
    ], indices = [Index("cardId"), Index(value = ["transactionId"], unique = true), Index("ownerId")]
)
data class PassEntity(@Embedded val value: TransportPass, val ownerId: String = WalletOwner.GUEST)

@Entity(
    tableName = "offers", primaryKeys = ["id"],
    foreignKeys = [ForeignKey(entity = MerchantEntity::class, parentColumns = ["id"], childColumns = ["merchantId"])],
    indices = [Index("merchantId")]
)
// Legacy embedded state is retained on disk for existing installs. Read only catalogue columns.
data class OfferEntity(@Embedded val value: Offer)

@Entity(
    tableName = "offer_states", primaryKeys = ["ownerId", "offerId"],
    foreignKeys = [ForeignKey(
        entity = OfferEntity::class, parentColumns = ["id"], childColumns = ["offerId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("offerId")]
)
data class OfferStateEntity(
    val ownerId: String, val offerId: String, val isFavorite: Boolean,
    val redeemedAt: Long?, val couponCode: String?
)

@Entity(
    tableName = "subsidy_claims", primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(entity = CardEntity::class, parentColumns = ["id"], childColumns = ["cardId"]),
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"])
    ], indices = [Index(value = ["cardId", "month"], unique = true), Index("transactionId"), Index("ownerId")]
)
data class SubsidyClaimEntity(@Embedded val value: SubsidyClaim, val ownerId: String = WalletOwner.GUEST)

/** Persisted idempotency key and immutable receipt snapshot, committed with the ledger. */
@Entity(tableName = "operations")
data class OperationEntity(
    @PrimaryKey val id: String,
    val requestFingerprint: String,
    val transactionId: String,
    val reloadTransactionId: String?,
    val passId: String?,
    val finalBalanceCents: Long,
    val ownerId: String = WalletOwner.GUEST
)

/**
 * Small key/value table for one-off storage facts (for example: the one-time adoption of the demo
 * wallet after the owner column was introduced). Kept out of `preferences` so internal state never
 * becomes a user-visible setting.
 */
@Entity(tableName = "app_meta")
data class MetaEntity(@PrimaryKey val key: String, val value: String)
