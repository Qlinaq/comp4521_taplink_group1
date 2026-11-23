package com.taplink.app.data

import androidx.room.withTransaction
import com.taplink.app.domain.*
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RoomTapLinkRepository(
    private val database: TapLinkDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) : TapLinkRepository {
    private val dao = database.dao()
    private val initialization = Mutex()
    private val seededOwners = mutableSetOf<String>()

    /**
     * Whose rows the flows and mutations touch. A flow, not a plain field, so an already-collected
     * flow (the widget, a worker) switches when the account changes instead of showing stale data.
     */
    private val ownerFlow = MutableStateFlow(WalletOwner.GUEST)
    @Volatile private var owner: String = WalletOwner.GUEST
    private val zone = ZoneId.of("Asia/Hong_Kong")

    @Synchronized
    override fun useOwner(ownerId: String) {
        val next = ownerId.ifBlank { WalletOwner.GUEST }
        if (next == ownerFlow.value) return
        owner = next
        ownerFlow.value = next
    }

    override val cards = observe { o -> dao.observeCards(o).map { rows -> rows.map { it.value } } }
    override val selectedCard = observe { o -> dao.observeSelectedCard(o).map { it?.value } }
    override val transactions = observe { o -> dao.observeTransactions(o).map { rows -> rows.map { it.value } } }
    override val merchants = observe { _ -> dao.observeMerchants().map { rows -> rows.map { it.value } } }
    override val locations = observe { _ -> dao.observeLocations().map { rows -> rows.map { it.value } } }
    override val messages = observe { o -> dao.observeMessages(o).map { rows -> rows.map { it.value } } }
    override val preferences = observe { _ -> dao.observePreferences().map { checkNotNull(it).value } }
    override val passes = observe { o -> dao.observePasses(o).map { rows -> rows.map { it.value } } }
    override val offers = observe { _ -> dao.observeOffers().map { rows -> rows.map { it.value } } }
    override val subsidyClaims = observe { o -> dao.observeSubsidyClaims(o).map { rows -> rows.map { it.value } } }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> observe(source: (String) -> Flow<T>): Flow<T> = ownerFlow
        .flatMapLatest { current -> flow { initialize(current); emitAll(source(current)) } }
        .distinctUntilChanged()

    override suspend fun initialize() = initialize(owner)

    private suspend fun initialize(ownerId: String) = withContext(Dispatchers.IO) {
        if (ownerId in seededOwners) return@withContext
        initialization.withLock {
            if (ownerId in seededOwners) return@withLock
            database.withTransaction {
                adoptLegacyWalletOnce(ownerId)
                if (dao.cardCount(ownerId) == 0) SeedData.populate(dao, clock(), ownerId)
            }
            seededOwners += ownerId
        }
    }

    /**
     * Before version 5 every row belonged to the single installation wallet. An upgraded database
     * therefore already holds rows when it is first opened, and the first signed-in account takes
     * them over so an update does not look like data loss. A fresh database has no rows at that
     * moment: its demo wallet is seeded immediately afterwards and must stay separate, so the
     * marker stops any later account from adopting it.
     */
    private suspend fun adoptLegacyWalletOnce(ownerId: String) {
        if (dao.meta(META_LEGACY_ADOPTED) != null) return
        val guestCards = dao.cardCount(WalletOwner.GUEST)
        when {
            ownerId != WalletOwner.GUEST -> {
                if (dao.cardCount(ownerId) == 0 && guestCards > 0) {
                    dao.adoptCards(ownerId, WalletOwner.GUEST)
                    dao.adoptTransactions(ownerId, WalletOwner.GUEST)
                    dao.adoptMessages(ownerId, WalletOwner.GUEST)
                    dao.adoptPasses(ownerId, WalletOwner.GUEST)
                    dao.adoptClaims(ownerId, WalletOwner.GUEST)
                    dao.adoptOperations(ownerId, WalletOwner.GUEST)
                }
                dao.putMeta(MetaEntity(META_LEGACY_ADOPTED, "1"))
            }
            // Demo wallet first, on a database that was empty: nothing to adopt, ever.
            guestCards == 0 -> dao.putMeta(MetaEntity(META_LEGACY_ADOPTED, "1"))
            // Demo wallet first, on an upgraded database: leave the wallet claimable by the account
            // that signs in next.
            else -> Unit
        }
    }

    override suspend fun createCard(name: String): WalletResult<Card> = mutate {
        val card = Card(newId(), WalletRules.cleanName(name), "•••• ${UUID.randomUUID().toString().takeLast(4).uppercase()}",
            0, false, clock(), listOf("#167D68", "#435D9B", "#AD7044")[dao.allCards(owner).size % 3])
        dao.insertCard(CardEntity(card, owner))
        card
    }

    override suspend fun renameCard(cardId: String, name: String): WalletResult<Unit> = mutate {
        val card = requireCard(cardId)
        dao.updateCard(CardEntity(card.copy(name = WalletRules.cleanName(name)), owner))
    }

    override suspend fun setCardFrozen(cardId: String, frozen: Boolean): WalletResult<Unit> = mutate {
        dao.updateCard(CardEntity(requireCard(cardId).copy(isFrozen = frozen), owner))
    }

    override suspend fun selectCard(cardId: String): WalletResult<Unit> = mutate {
        requireCard(cardId)
        dao.savePreferences(PreferencesEntity(value = currentPreferences().copy(selectedCardId = cardId)))
    }

    override suspend fun updatePreferences(preferences: AppPreferences): WalletResult<Unit> = mutate {
        saveValidatedPreferences(preferences, currentPreferences())
    }

    override suspend fun transformPreferences(change: (AppPreferences) -> AppPreferences): WalletResult<Unit> = mutate {
        val latest = currentPreferences()
        saveValidatedPreferences(change(latest), latest)
    }

    private suspend fun saveValidatedPreferences(preferences: AppPreferences, latest: AppPreferences) {
        WalletRules.validatePreferences(preferences)
        val selectedId = preferences.selectedCardId ?: latest.selectedCardId
        selectedId?.let { requireCard(it) }
        dao.savePreferences(PreferencesEntity(value = preferences.copy(selectedCardId = selectedId,
            manualDistrict = preferences.manualDistrict.trim())))
    }

    override suspend fun topUp(cardId: String, amountCents: Long, operationId: String): WalletResult<Receipt> = mutate {
        val fingerprint = fingerprint("topUp", cardId, amountCents.toString())
        replay(operationId, fingerprint)?.let { return@mutate it }
        val card = requireCard(cardId)
        val finalBalance = WalletRules.credit(card.balanceCents, card.isFrozen, amountCents)
        val transaction = WalletTransaction(newId(), operationId, card.id, TransactionType.TOP_UP,
            amountCents, finalBalance, null, "Demo funding", Category.OTHER, clock(), "Simulated top-up; no real money moved.")
        val receipt = commit(card, Receipt(transaction, null, finalBalance), fingerprint)
        // The inbox keeps every notification; the notification category only decides whether an
        // OS-level alert is also posted.
        recordTransactionMessage(transaction, card.name, amountCents)
        receipt
    }

    override suspend fun pay(
        cardId: String, merchantId: String, amountCents: Long, operationId: String
    ): WalletResult<Receipt> = debit(cardId, merchantId, amountCents, operationId, TransactionType.PAYMENT)

    override suspend fun buyPass(cardId: String, operationId: String): WalletResult<Receipt> =
        debit(cardId, null, PassCatalog.MONTHLY_PASS_PRICE_CENTS, operationId, TransactionType.PASS_PURCHASE)

    private suspend fun debit(
        cardId: String, merchantId: String?, amountCents: Long, operationId: String, type: TransactionType
    ): WalletResult<Receipt> = mutate {
        val fingerprint = fingerprint(type.name, cardId, merchantId.orEmpty(), amountCents.toString())
        replay(operationId, fingerprint)?.let { return@mutate it }
        val card = requireCard(cardId)
        val now = clock()
        val prefs = currentPreferences()
        val merchant = if (type == TransactionType.PAYMENT) {
            dao.merchant(checkNotNull(merchantId))?.value ?: fail(WalletError.NOT_FOUND, "Select an available demo merchant.")
        } else null
        val plan = WalletRules.payment(card.balanceCents, card.isFrozen, amountCents, prefs)
        if (type == TransactionType.PASS_PURCHASE && dao.activePass(cardId, now, owner) != null) {
            fail(WalletError.PASS_ALREADY_ACTIVE, "This card already has an active demo monthly pass.")
        }
        val transaction = WalletTransaction(newId(), operationId, cardId, type, amountCents,
            plan.balanceAfterPaymentCents, merchant?.id, merchant?.name ?: "TapLink Demo Transport",
            merchant?.category ?: Category.TRANSPORT, now,
            if (type == TransactionType.PAYMENT) "Simulated merchant payment." else "Simulated monthly pass; not a real transport ticket.")
        val reload = if (plan.reloadCents > 0) WalletTransaction(newId(), operationId, cardId,
            TransactionType.AUTO_RELOAD, plan.reloadCents, plan.finalBalanceCents, null,
            "Automatic demo reload", Category.OTHER, now, "One automatic reload after an affordable debit.") else null
        val pass = if (type == TransactionType.PASS_PURCHASE) TransportPass(newId(), cardId,
            PassCatalog.MONTHLY_PASS_NAME, amountCents, now,
            Instant.ofEpochMilli(now).atZone(zone).plusMonths(1).toInstant().toEpochMilli(), transaction.id) else null
        val receipt = commit(card, Receipt(transaction, reload, plan.finalBalanceCents, pass), fingerprint)
        recordTransactionMessage(transaction, card.name, amountCents)
        if (plan.shouldAlert) {
            dao.insertMessage(MessageEntity(WalletMessage("low-balance:$operationId", "Low demo balance",
                "${card.name} has ${Money.format(plan.finalBalanceCents)} remaining after your simulated payment.",
                MessageType.LOW_BALANCE, now, false, cardId, transaction.id, MessageKind.LOW_BALANCE,
                plan.finalBalanceCents), owner))
        }
        receipt
    }

    /**
     * English text is stored so the row is still readable in a database dump; [MessageKind] and the
     * amount let the UI render the same event in the chosen interface language.
     */
    private suspend fun recordTransactionMessage(
        transaction: WalletTransaction,
        cardName: String,
        amountCents: Long
    ) {
        val title = when (transaction.type) {
            TransactionType.TOP_UP -> "Top-up completed"
            TransactionType.PASS_PURCHASE -> "Pass purchased"
            else -> "Payment completed"
        }
        val body = when (transaction.type) {
            TransactionType.TOP_UP -> "${Money.format(amountCents)} was added to $cardName."
            TransactionType.PASS_PURCHASE -> "${Money.format(amountCents)} was used for ${PassCatalog.MONTHLY_PASS_NAME} on $cardName."
            else -> "${Money.format(amountCents)} was paid from $cardName to ${transaction.merchantName}."
        }
        val kind = when (transaction.type) {
            TransactionType.TOP_UP -> MessageKind.TOP_UP
            TransactionType.PASS_PURCHASE -> MessageKind.PASS_PURCHASE
            else -> MessageKind.PAYMENT
        }
        dao.insertMessage(MessageEntity(WalletMessage(
            "transaction:${transaction.operationId}", title, body,
            MessageType.TRANSACTION, transaction.createdAt, false, transaction.cardId,
            transaction.id, kind, amountCents
        ), owner))
    }

    /** The caller is always inside one Room transaction, including every dependent record. */
    private suspend fun commit(card: Card, receipt: Receipt, fingerprint: String): Receipt {
        dao.updateCard(CardEntity(card.copy(balanceCents = receipt.finalBalanceCents), owner))
        dao.insertTransaction(TransactionEntity(receipt.transaction, owner))
        receipt.autoReload?.let { dao.insertTransaction(TransactionEntity(it, owner)) }
        receipt.pass?.let { dao.insertPass(PassEntity(it, owner)) }
        dao.insertOperation(OperationEntity(receipt.transaction.operationId, fingerprint,
            receipt.transaction.id, receipt.autoReload?.id, receipt.pass?.id, receipt.finalBalanceCents, owner))
        return receipt
    }

    override suspend fun getReceipt(operationId: String): Receipt? = withContext(Dispatchers.IO) {
        initialize()
        database.withTransaction { dao.operation(operationId, owner)?.let { receipt(it) } }
    }

    private suspend fun replay(operationId: String, fingerprint: String): Receipt? {
        WalletRules.validateOperationId(operationId)
        val operation = dao.operation(operationId, owner) ?: return null
        if (operation.requestFingerprint != fingerprint) {
            fail(WalletError.IDEMPOTENCY_CONFLICT, "This confirmation ID was already used for different details. Start a new confirmation.")
        }
        return receipt(operation)
    }

    private suspend fun receipt(operation: OperationEntity): Receipt = Receipt(
        checkNotNull(dao.transaction(operation.transactionId, owner)).value,
        operation.reloadTransactionId?.let { checkNotNull(dao.transaction(it, owner)).value },
        operation.finalBalanceCents,
        operation.passId?.let { checkNotNull(dao.pass(it, owner)).value }
    )

    override suspend fun markMessageRead(messageId: String): WalletResult<Unit> = mutate {
        if (dao.markMessageRead(messageId, owner) == 0) fail(WalletError.NOT_FOUND, "This message could not be found.")
    }

    override suspend fun markAllMessagesRead(): WalletResult<Unit> = mutate { dao.markAllMessagesRead(owner) }

    override suspend fun setOfferFavorite(offerId: String, favorite: Boolean): WalletResult<Unit> = mutate {
        val offer = requireOffer(offerId)
        dao.updateOffer(OfferEntity(offer.copy(isFavorite = favorite)))
    }

    override suspend fun redeemOffer(offerId: String): WalletResult<Offer> = mutate {
        val offer = requireOffer(offerId)
        if (offer.redeemedAt != null) fail(WalletError.ALREADY_REDEEMED, "This demo coupon has already been redeemed.")
        val now = clock()
        if (now >= offer.expiresAt) fail(WalletError.OFFER_EXPIRED, "This demo offer has expired.")
        offer.copy(redeemedAt = now, couponCode = "DEMO-${newId().take(8).uppercase()}").also {
            dao.updateOffer(OfferEntity(it))
        }
    }

    override suspend fun claimSubsidy(cardId: String, month: String, operationId: String): WalletResult<Receipt> = mutate {
        val fingerprint = fingerprint("subsidy", cardId, month)
        replay(operationId, fingerprint)?.let { return@mutate it }
        val requestedMonth = try {
            if (!Regex("[0-9]{4}-[0-9]{2}").matches(month)) fail(WalletError.INVALID_MONTH, "Choose a completed month in YYYY-MM format.")
            YearMonth.parse(month)
        } catch (_: DateTimeParseException) {
            fail(WalletError.INVALID_MONTH, "Choose a completed month in YYYY-MM format.")
        }
        val now = clock()
        if (requestedMonth >= YearMonth.from(Instant.ofEpochMilli(now).atZone(zone))) {
            fail(WalletError.INVALID_MONTH, "Demo subsidy claims are available only for completed months.")
        }
        val card = requireCard(cardId)
        if (dao.claim(cardId, month, owner) != null) fail(WalletError.ALREADY_CLAIMED, "This card already claimed its demo subsidy for this month.")
        val spending = WalletAnalytics.monthlySpending(dao.allTransactions(owner).map { it.value }.filter { it.cardId == cardId }, requestedMonth)
            .byCategory[Category.TRANSPORT] ?: 0L
        val amount = WalletAnalytics.fictionalSubsidy(spending)
        if (amount == 0L) fail(WalletError.NOTHING_TO_CLAIM, "No demo subsidy is available under the fictional rule for this month.")
        val finalBalance = WalletRules.credit(card.balanceCents, card.isFrozen, amount)
        val transaction = WalletTransaction(newId(), operationId, cardId, TransactionType.SUBSIDY_CREDIT,
            amount, finalBalance, null, "Fictional transport subsidy", Category.TRANSPORT, now,
            "Demo rule for $month: 20% above HK$200, capped at HK$100. Not an official entitlement.")
        val receipt = commit(card, Receipt(transaction, null, finalBalance), fingerprint)
        dao.insertClaim(SubsidyClaimEntity(SubsidyClaim(newId(), cardId, month, spending, amount, now, transaction.id), owner))
        receipt
    }

    override suspend fun refreshDemoContent(): WalletResult<Unit> = mutate {
        if (currentPreferences().demoRefreshEnabled) SeedData.ensureCatalog(dao, clock())
    }

    override suspend fun generateReminders(now: Long): WalletResult<Int> = mutate {
        val prefs = currentPreferences()
        if (!prefs.remindersEnabled || !prefs.expiryRemindersEnabled) return@mutate 0
        val horizon = 3L * 86_400_000L
        var count = 0
        for (pass in dao.allPasses(owner).map { it.value }) {
            if (pass.validUntil > now && pass.validUntil - now <= horizon) {
                val inserted = dao.insertMessage(MessageEntity(WalletMessage("pass-expiry:${pass.id}:${pass.validUntil}",
                    "Demo pass expires soon", "${pass.name} expires on ${Instant.ofEpochMilli(pass.validUntil).atZone(zone).toLocalDate()}.",
                    MessageType.PASS_EXPIRY, now, false, pass.cardId, pass.transactionId, MessageKind.PASS_EXPIRY), owner))
                if (inserted != -1L) count++
            }
        }
        for (offer in dao.allOffers().map { it.value }) {
            if (offer.isFavorite && offer.redeemedAt == null && offer.expiresAt > now && offer.expiresAt - now <= horizon) {
                val inserted = dao.insertMessage(MessageEntity(WalletMessage("offer-expiry:${offer.id}:${offer.expiresAt}",
                    "Saved demo offer expires soon", "${offer.title} expires on ${Instant.ofEpochMilli(offer.expiresAt).atZone(zone).toLocalDate()}.",
                    MessageType.OFFER_EXPIRY, now, false, kind = MessageKind.OFFER_EXPIRY), owner))
                if (inserted != -1L) count++
            }
        }
        count
    }

    override fun observeTransactions(filter: TransactionFilter): Flow<List<WalletTransaction>> =
        transactions.map { WalletAnalytics.filter(it, filter) }.distinctUntilChanged()

    private suspend fun requireCard(id: String): Card = dao.card(id, owner)?.value
        ?: fail(WalletError.NOT_FOUND, "This card could not be found.")

    private suspend fun requireOffer(id: String): Offer = dao.offer(id)?.value
        ?: fail(WalletError.NOT_FOUND, "This demo offer could not be found.")

    private suspend fun currentPreferences(): AppPreferences = checkNotNull(dao.preferences()).value

    private suspend fun <T> mutate(block: suspend () -> T): WalletResult<T> = withContext(Dispatchers.IO) {
        try {
            initialize()
            WalletResult.Success(database.withTransaction { block() })
        } catch (cancelled: CancellationException) {
            // This does not imply rollback: cancellation may discard a result after commit.
            // The persisted operation ID supports receipt lookup and exactly-once replay.
            throw cancelled
        } catch (rule: WalletRuleException) {
            WalletResult.Error(rule.code, rule.message)
        } catch (_: Exception) {
            WalletResult.Error(WalletError.STORAGE_ERROR, "The local update could not be completed. Please try again; no partial changes were saved.")
        }
    }

    private companion object {
        /** Set once, after the demo wallet has been handed to the first signed-in account. */
        const val META_LEGACY_ADOPTED = "legacy-wallet-adopted"
    }

    private fun fingerprint(vararg parts: String): String = parts.joinToString("") { "${it.length}:$it" }
    private fun newId(): String = UUID.randomUUID().toString()
    private fun fail(code: WalletError, message: String): Nothing = throw WalletRuleException(code, message)
}
