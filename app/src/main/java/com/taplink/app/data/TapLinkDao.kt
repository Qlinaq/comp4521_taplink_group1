package com.taplink.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Every wallet query takes an owner so one installation can hold the demo wallet and one wallet per
 * account without the two ever meeting. Catalogue queries (merchants, locations, offers) are shared.
 */
@Dao
interface TapLinkDao {
    @Query("SELECT * FROM cards WHERE ownerId = :owner ORDER BY createdAt, id")
    fun observeCards(owner: String): Flow<List<CardEntity>>

    @Query(
        "SELECT cards.* FROM cards INNER JOIN preferences ON cards.id = preferences.selectedCardId " +
            "WHERE preferences.singletonId = 1 AND cards.ownerId = :owner"
    )
    fun observeSelectedCard(owner: String): Flow<CardEntity?>

    @Query("SELECT * FROM transactions WHERE ownerId = :owner ORDER BY createdAt DESC, ledgerSequence DESC")
    fun observeTransactions(owner: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM merchants ORDER BY name")
    fun observeMerchants(): Flow<List<MerchantEntity>>

    @Query("SELECT * FROM locations ORDER BY district, name")
    fun observeLocations(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM preferences WHERE singletonId = 1")
    fun observePreferences(): Flow<PreferencesEntity?>

    @Query("SELECT * FROM messages WHERE ownerId = :owner ORDER BY createdAt DESC, id DESC")
    fun observeMessages(owner: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM passes WHERE ownerId = :owner ORDER BY validFrom DESC, id DESC")
    fun observePasses(owner: String): Flow<List<PassEntity>>

    @Query("SELECT * FROM offers ORDER BY expiresAt, id")
    fun observeOffers(): Flow<List<OfferEntity>>

    @Query("SELECT * FROM subsidy_claims WHERE ownerId = :owner ORDER BY claimedAt DESC, id DESC")
    fun observeSubsidyClaims(owner: String): Flow<List<SubsidyClaimEntity>>

    @Query("SELECT * FROM cards WHERE id = :id AND ownerId = :owner")
    suspend fun card(id: String, owner: String): CardEntity?

    @Query("SELECT * FROM cards WHERE ownerId = :owner ORDER BY createdAt, id")
    suspend fun allCards(owner: String): List<CardEntity>

    @Query("SELECT COUNT(*) FROM cards WHERE ownerId = :owner")
    suspend fun cardCount(owner: String): Int

    @Query("SELECT * FROM merchants WHERE id = :id")
    suspend fun merchant(id: String): MerchantEntity?

    @Query("SELECT * FROM preferences WHERE singletonId = 1")
    suspend fun preferences(): PreferencesEntity?

    @Query("SELECT * FROM operations WHERE id = :id AND ownerId = :owner")
    suspend fun operation(id: String, owner: String): OperationEntity?

    @Query("SELECT * FROM transactions WHERE id = :id AND ownerId = :owner")
    suspend fun transaction(id: String, owner: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE ownerId = :owner ORDER BY createdAt DESC, ledgerSequence DESC")
    suspend fun allTransactions(owner: String): List<TransactionEntity>

    @Query("SELECT * FROM passes WHERE id = :id AND ownerId = :owner")
    suspend fun pass(id: String, owner: String): PassEntity?

    @Query("SELECT * FROM passes WHERE ownerId = :owner")
    suspend fun allPasses(owner: String): List<PassEntity>

    @Query("SELECT * FROM passes WHERE ownerId = :owner AND cardId = :cardId AND validFrom <= :now AND validUntil > :now LIMIT 1")
    suspend fun activePass(cardId: String, now: Long, owner: String): PassEntity?

    @Query("SELECT * FROM offers WHERE id = :id")
    suspend fun offer(id: String): OfferEntity?

    @Query("SELECT * FROM offers")
    suspend fun allOffers(): List<OfferEntity>

    @Query("SELECT * FROM subsidy_claims WHERE ownerId = :owner AND cardId = :cardId AND month = :month LIMIT 1")
    suspend fun claim(cardId: String, month: String, owner: String): SubsidyClaimEntity?

    @Query("SELECT * FROM messages WHERE id = :id AND ownerId = :owner")
    suspend fun message(id: String, owner: String): MessageEntity?

    @Query("SELECT value FROM app_meta WHERE key = :key")
    suspend fun meta(key: String): String?

    @Insert suspend fun insertCard(card: CardEntity)
    @Update suspend fun updateCard(card: CardEntity)
    @Insert suspend fun insertTransaction(transaction: TransactionEntity)
    @Insert suspend fun insertOperation(operation: OperationEntity)
    @Insert suspend fun insertPass(pass: PassEntity)
    @Insert suspend fun insertClaim(claim: SubsidyClaimEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun savePreferences(preferences: PreferencesEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMeta(meta: MetaEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertMerchants(merchants: List<MerchantEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertLocations(locations: List<LocationEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertOffers(offers: List<OfferEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertMessage(message: MessageEntity): Long
    @Update suspend fun updateOffer(offer: OfferEntity)

    @Query("UPDATE messages SET isRead = 1 WHERE id = :id AND ownerId = :owner")
    suspend fun markMessageRead(id: String, owner: String): Int

    @Query("UPDATE messages SET isRead = 1 WHERE isRead = 0 AND ownerId = :owner")
    suspend fun markAllMessagesRead(owner: String)

    // One-time upgrade: rows written before wallets were scoped belong to whoever was using the app,
    // and the repository adopts them into the signed-in account the first time it opens.
    @Query("UPDATE cards SET ownerId = :owner WHERE ownerId = :from")
    suspend fun adoptCards(owner: String, from: String)

    @Query("UPDATE transactions SET ownerId = :owner WHERE ownerId = :from")
    suspend fun adoptTransactions(owner: String, from: String)

    @Query("UPDATE messages SET ownerId = :owner WHERE ownerId = :from")
    suspend fun adoptMessages(owner: String, from: String)

    @Query("UPDATE passes SET ownerId = :owner WHERE ownerId = :from")
    suspend fun adoptPasses(owner: String, from: String)

    @Query("UPDATE subsidy_claims SET ownerId = :owner WHERE ownerId = :from")
    suspend fun adoptClaims(owner: String, from: String)

    @Query("UPDATE operations SET ownerId = :owner WHERE ownerId = :from")
    suspend fun adoptOperations(owner: String, from: String)
}
