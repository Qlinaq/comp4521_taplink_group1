package com.taplink.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.taplink.app.domain.*

@Entity(tableName = "cards", primaryKeys = ["id"])
data class CardEntity(@Embedded val value: Card)

@Entity(tableName = "merchants", primaryKeys = ["id"])
data class MerchantEntity(@Embedded val value: Merchant)

@Entity(
    tableName = "transactions",
    foreignKeys = [ForeignKey(entity = CardEntity::class, parentColumns = ["id"], childColumns = ["cardId"])],
    indices = [Index(value = ["id"], unique = true), Index("cardId"), Index("createdAt"),
        Index(value = ["operationId", "type"], unique = true)]
)
data class TransactionEntity(
    @Embedded val value: WalletTransaction,
    // SQLite assigns this in insertion order inside the same transaction as the money operation.
    // Unlike a UUID or wall clock, it preserves debit/reload order when timestamps tie.
    @PrimaryKey(autoGenerate = true) val ledgerSequence: Long = 0
)

@Entity(tableName = "preferences")
data class PreferencesEntity(@PrimaryKey val singletonId: Int = 1, @Embedded val value: AppPreferences)

@Entity(tableName = "messages", primaryKeys = ["id"], indices = [Index("createdAt")])
data class MessageEntity(@Embedded val value: WalletMessage)

@Entity(tableName = "locations", primaryKeys = ["id"])
data class LocationEntity(@Embedded val value: ServiceLocation)

@Entity(
    tableName = "passes", primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(entity = CardEntity::class, parentColumns = ["id"], childColumns = ["cardId"]),
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"])
    ], indices = [Index("cardId"), Index(value = ["transactionId"], unique = true)]
)
data class PassEntity(@Embedded val value: TransportPass)

@Entity(
    tableName = "offers", primaryKeys = ["id"],
    foreignKeys = [ForeignKey(entity = MerchantEntity::class, parentColumns = ["id"], childColumns = ["merchantId"])],
    indices = [Index("merchantId")]
)
data class OfferEntity(@Embedded val value: Offer)

@Entity(
    tableName = "subsidy_claims", primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(entity = CardEntity::class, parentColumns = ["id"], childColumns = ["cardId"]),
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"])
    ], indices = [Index(value = ["cardId", "month"], unique = true), Index("transactionId")]
)
data class SubsidyClaimEntity(@Embedded val value: SubsidyClaim)

/** Persisted idempotency key and immutable receipt snapshot, committed with the ledger. */
@Entity(tableName = "operations")
data class OperationEntity(
    @PrimaryKey val id: String,
    val requestFingerprint: String,
    val transactionId: String,
    val reloadTransactionId: String?,
    val passId: String?,
    val finalBalanceCents: Long
)
