package com.taplink.app.domain

import kotlinx.coroutines.flow.Flow

/**
 * Money-operation IDs must survive retries and lifecycle cancellation. Cancellation before commit
 * rolls back; cancellation during result delivery can follow a successful commit. Recover using
 * getReceipt or replay the same inputs and operation ID from an active coroutine; do not treat
 * cancellation as proof of failure or replace the ID for the same intended operation.
 */
interface TapLinkRepository {
    val cards: Flow<List<Card>>
    val selectedCard: Flow<Card?>
    /** Newest timestamp first, with persisted ledger insertion order breaking equal-time ties. */
    val transactions: Flow<List<WalletTransaction>>
    val merchants: Flow<List<Merchant>>
    val locations: Flow<List<ServiceLocation>>
    val messages: Flow<List<WalletMessage>>
    fun observeMessages(ownerId: String): Flow<List<WalletMessage>> = messages
    val preferences: Flow<AppPreferences>
    val passes: Flow<List<TransportPass>>
    val offers: Flow<List<Offer>>
    fun observeOffers(ownerId: String): Flow<List<Offer>> = offers
    val subsidyClaims: Flow<List<SubsidyClaim>>

    suspend fun initialize()

    /**
     * Selects whose wallet the flows and mutations below operate on. Called once when the wallet
     * shell is created for a session, so a different account never reads the previous one's rows.
     */
    fun useOwner(ownerId: String)
    suspend fun createCard(name: String): WalletResult<Card>
    suspend fun renameCard(cardId: String, name: String): WalletResult<Unit>
    suspend fun setCardFrozen(cardId: String, frozen: Boolean): WalletResult<Unit>
    suspend fun selectCard(cardId: String): WalletResult<Unit>
    suspend fun topUp(cardId: String, amountCents: Long, operationId: String): WalletResult<Receipt>
    suspend fun pay(cardId: String, merchantId: String, amountCents: Long, operationId: String): WalletResult<Receipt>
    suspend fun buyPass(cardId: String, operationId: String): WalletResult<Receipt>
    suspend fun updatePreferences(preferences: AppPreferences): WalletResult<Unit>
    /** Applies a quick, side-effect-free transform to the latest preferences inside one transaction. */
    suspend fun transformPreferences(change: (AppPreferences) -> AppPreferences): WalletResult<Unit>
    suspend fun markMessageRead(messageId: String, ownerId: String? = null): WalletResult<Unit>
    suspend fun markAllMessagesRead(ownerId: String? = null): WalletResult<Unit>
    suspend fun setOfferFavorite(offerId: String, favorite: Boolean, ownerId: String? = null): WalletResult<Unit>
    suspend fun redeemOffer(offerId: String, ownerId: String? = null): WalletResult<Offer>
    suspend fun claimSubsidy(cardId: String, month: String, operationId: String): WalletResult<Receipt>
    suspend fun refreshDemoContent(): WalletResult<Unit>
    suspend fun generateReminders(now: Long = System.currentTimeMillis()): WalletResult<Int>
    suspend fun getReceipt(operationId: String): Receipt?
    fun observeTransactions(filter: TransactionFilter = TransactionFilter()): Flow<List<WalletTransaction>>
}
