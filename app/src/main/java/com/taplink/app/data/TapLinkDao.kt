package com.taplink.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TapLinkDao {
    @Query("SELECT * FROM cards ORDER BY createdAt, id")
    fun observeCards(): Flow<List<CardEntity>>

    @Query("SELECT cards.* FROM cards INNER JOIN preferences ON cards.id = preferences.selectedCardId WHERE preferences.singletonId = 1")
    fun observeSelectedCard(): Flow<CardEntity?>

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC, ledgerSequence DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM merchants ORDER BY name")
    fun observeMerchants(): Flow<List<MerchantEntity>>

    @Query("SELECT * FROM locations ORDER BY district, name")
    fun observeLocations(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM preferences WHERE singletonId = 1")
    fun observePreferences(): Flow<PreferencesEntity?>

    @Query("SELECT * FROM messages ORDER BY createdAt DESC, id DESC")
    fun observeMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM passes ORDER BY validFrom DESC, id DESC")
    fun observePasses(): Flow<List<PassEntity>>

    @Query("SELECT * FROM offers ORDER BY expiresAt, id")
    fun observeOffers(): Flow<List<OfferEntity>>

    @Query("SELECT * FROM subsidy_claims ORDER BY claimedAt DESC, id DESC")
    fun observeSubsidyClaims(): Flow<List<SubsidyClaimEntity>>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun card(id: String): CardEntity?

    @Query("SELECT * FROM cards ORDER BY createdAt, id")
    suspend fun allCards(): List<CardEntity>

    @Query("SELECT * FROM merchants WHERE id = :id")
    suspend fun merchant(id: String): MerchantEntity?

    @Query("SELECT * FROM preferences WHERE singletonId = 1")
    suspend fun preferences(): PreferencesEntity?

    @Query("SELECT * FROM operations WHERE id = :id")
    suspend fun operation(id: String): OperationEntity?

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun transaction(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC, ledgerSequence DESC")
    suspend fun allTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM passes WHERE id = :id")
    suspend fun pass(id: String): PassEntity?

    @Query("SELECT * FROM passes")
    suspend fun allPasses(): List<PassEntity>

    @Query("SELECT * FROM passes WHERE cardId = :cardId AND validFrom <= :now AND validUntil > :now LIMIT 1")
    suspend fun activePass(cardId: String, now: Long): PassEntity?

    @Query("SELECT * FROM offers WHERE id = :id")
    suspend fun offer(id: String): OfferEntity?

    @Query("SELECT * FROM offers")
    suspend fun allOffers(): List<OfferEntity>

    @Query("SELECT * FROM subsidy_claims WHERE cardId = :cardId AND month = :month LIMIT 1")
    suspend fun claim(cardId: String, month: String): SubsidyClaimEntity?

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun message(id: String): MessageEntity?

    @Insert suspend fun insertCard(card: CardEntity)
    @Update suspend fun updateCard(card: CardEntity)
    @Insert suspend fun insertTransaction(transaction: TransactionEntity)
    @Insert suspend fun insertOperation(operation: OperationEntity)
    @Insert suspend fun insertPass(pass: PassEntity)
    @Insert suspend fun insertClaim(claim: SubsidyClaimEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun savePreferences(preferences: PreferencesEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertMerchants(merchants: List<MerchantEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertLocations(locations: List<LocationEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertOffers(offers: List<OfferEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertMessage(message: MessageEntity): Long
    @Update suspend fun updateOffer(offer: OfferEntity)

    @Query("UPDATE messages SET isRead = 1 WHERE id = :id")
    suspend fun markMessageRead(id: String): Int

    @Query("UPDATE messages SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllMessagesRead()
}
