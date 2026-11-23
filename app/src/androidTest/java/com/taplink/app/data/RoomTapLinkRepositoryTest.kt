package com.taplink.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.taplink.app.domain.*
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTapLinkRepositoryTest {
    private lateinit var database: TapLinkDatabase
    private lateinit var repository: RoomTapLinkRepository
    private val now = Instant.parse("2026-11-22T12:00:00Z").toEpochMilli()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, TapLinkDatabase::class.java).build()
        repository = RoomTapLinkRepository(database) { now }
        repository.initialize()
    }

    @After fun tearDown() { database.close() }

    @Test fun seedBalancesAgreeWithFullLedgerAndInitializationIsIdempotent() = runBlocking {
        repeat(3) { repository.initialize() }
        val cards = repository.cards.first()
        val ledger = repository.transactions.first()
        assertEquals(3, cards.size)
        assertTrue(repository.merchants.first().size >= 3)
        assertTrue(repository.locations.first().size >= 3)
        assertTrue(repository.offers.first().size >= 3)
        for (card in cards) {
            assertEquals(card.balanceCents, ledger.filter { it.cardId == card.id }.sumOf { it.signedAmountCents })
        }
        assertEquals("card-campus", repository.selectedCard.first()?.id)
    }

    @Test fun cardManagementAndSelectionPersistInObservedState() = runBlocking {
        val card = value(repository.createCard("  New card  "))
        assertEquals("New card", card.name)
        assertEquals(0L, card.balanceCents)
        value(repository.renameCard(card.id, "Travel"))
        value(repository.setCardFrozen(card.id, true))
        value(repository.selectCard(card.id))
        val selected = repository.selectedCard.first()!!
        assertEquals("Travel", selected.name)
        assertTrue(selected.isFrozen)
        assertEquals(card.id, repository.preferences.first().selectedCardId)
        assertError(WalletError.INVALID_NAME, repository.renameCard(card.id, " "))
        assertError(WalletError.NOT_FOUND, repository.selectCard("missing"))
        assertEquals(card.id, repository.selectedCard.first()?.id)
    }

    @Test fun topUpStoresReceiptAndDoesNotRepeatAfterOtherOperations() = runBlocking {
        val card = freshCard(10000)
        val first = value(repository.topUp(card.id, 29, "topup"))
        assertEquals(10029L, first.finalBalanceCents)
        value(repository.pay(card.id, "campus-cafe", 100, "later-payment"))
        value(repository.setCardFrozen(card.id, true))
        assertEquals(first, value(repository.topUp(card.id, 29, "topup")))
        assertEquals(first, repository.getReceipt("topup"))
        assertEquals(9929L, balance(card.id))
        assertEquals(1, repository.transactions.first().count { it.operationId == "topup" })
        assertError(WalletError.IDEMPOTENCY_CONFLICT, repository.topUp(card.id, 30, "topup"))
        assertError(WalletError.IDEMPOTENCY_CONFLICT, repository.pay(card.id, "campus-cafe", 29, "topup"))
    }

    @Test fun preferenceTransformUsesLatestSelectionEvenWhenUiSnapshotIsStale() = runBlocking {
        val staleUiSnapshot = repository.preferences.first()
        value(repository.selectCard("card-commute"))
        value(repository.transformPreferences { latest -> latest.copy(theme = ThemeMode.DARK) })
        val saved = repository.preferences.first()
        assertEquals("card-campus", staleUiSnapshot.selectedCardId)
        assertEquals("card-commute", saved.selectedCardId)
        assertEquals("card-commute", repository.selectedCard.first()!!.id)
        assertEquals(ThemeMode.DARK, saved.theme)
    }

    @Test fun concurrentPreferenceTransformsPreserveIndependentFieldsAndCardSelection() = runBlocking {
        val results = coroutineScope {
            listOf(
                async(Dispatchers.Default) { repository.transformPreferences { it.copy(theme = ThemeMode.DARK) } },
                async(Dispatchers.Default) { repository.transformPreferences { it.copy(balanceVisible = false) } },
                async(Dispatchers.Default) { repository.transformPreferences { it.copy(autoReloadAmountCents = 12345) } },
                async(Dispatchers.Default) { repository.selectCard("card-commute") }
            ).awaitAll()
        }
        results.forEach { value(it) }
        val saved = repository.preferences.first()
        assertEquals(ThemeMode.DARK, saved.theme)
        assertFalse(saved.balanceVisible)
        assertEquals(12345L, saved.autoReloadAmountCents)
        assertEquals("card-commute", saved.selectedCardId)
    }

    @Test fun invalidPreferenceTransformRollsBackAllItsChanges() = runBlocking {
        val before = repository.preferences.first()
        assertError(WalletError.INVALID_PREFERENCES, repository.transformPreferences {
            it.copy(theme = ThemeMode.DARK, autoReloadAmountCents = 0)
        })
        assertEquals(before, repository.preferences.first())
        assertError(WalletError.NOT_FOUND, repository.transformPreferences {
            it.copy(selectedCardId = "missing-card", balanceVisible = false)
        })
        assertEquals(before, repository.preferences.first())
    }

    @Test fun affordablePaymentCommitsDebitOneReloadAndFinalBalanceAlert() = runBlocking {
        val card = freshCard(2000)
        value(repository.updatePreferences(repository.preferences.first().copy(
            autoReloadEnabled = true, autoReloadThresholdCents = 5000, autoReloadAmountCents = 100)))
        val receipt = value(repository.pay(card.id, "campus-cafe", 1500, "reload-payment"))
        assertEquals(500L, receipt.transaction.balanceAfterCents)
        assertEquals(100L, receipt.autoReload!!.amountCents)
        assertEquals(600L, receipt.finalBalanceCents)
        assertEquals(600L, balance(card.id))
        assertEquals(2, repository.transactions.first().count { it.operationId == "reload-payment" })
        assertEquals(listOf(TransactionType.AUTO_RELOAD, TransactionType.PAYMENT),
            repository.transactions.first().filter { it.operationId == "reload-payment" }.map { it.type })
        // Two messages now belong to this transaction: the inbox record of the payment and the
        // low-balance alert. Asserting both keeps the old alert guarantee while covering the new row.
        val written = repository.messages.first().filter { it.transactionId == receipt.transaction.id }
        assertEquals(2, written.size)
        val alert = written.single { it.type == MessageType.LOW_BALANCE }
        assertTrue(alert.body.contains("HK$6.00"))
        assertEquals(MessageKind.LOW_BALANCE, alert.kind)
        val record = written.single { it.type == MessageType.TRANSACTION }
        assertEquals(MessageKind.PAYMENT, record.kind)
        assertEquals(1500L, record.amountCents)
        assertTrue(record.body.contains("HK$15.00"))
        assertEquals(receipt, value(repository.pay(card.id, "campus-cafe", 1500, "reload-payment")))
        assertEquals(2, repository.messages.first().count { it.transactionId == receipt.transaction.id })
    }

    @Test fun equalTimestampLedgerOrderDoesNotDependOnLexicalIds() = runBlocking {
        val card = freshCard(6000)
        val payment = WalletTransaction("z-payment", "tied-operation", card.id,
            TransactionType.PAYMENT, 2000, 4000, "campus-cafe", "Campus Cafe", Category.FOOD, now, "Demo")
        val reload = payment.copy(id = "b-reload", type = TransactionType.AUTO_RELOAD,
            amountCents = 10000, balanceAfterCents = 14000, merchantId = null, merchantName = "Demo reload")
        val later = reload.copy(id = "a-later-credit", operationId = "later-operation",
            type = TransactionType.TOP_UP, amountCents = 100, balanceAfterCents = 14100)
        database.withTransaction {
            database.dao().insertTransaction(TransactionEntity(payment))
            database.dao().insertTransaction(TransactionEntity(reload))
            database.dao().insertTransaction(TransactionEntity(later))
            database.dao().updateCard(CardEntity(card.copy(balanceCents = 14100)))
        }
        val expectedIds = listOf("a-later-credit", "b-reload", "z-payment")
        val ids = expectedIds.toSet()
        assertEquals(expectedIds, repository.transactions.first().filter { it.id in ids }.map { it.id })
        val stored = database.dao().allTransactions(WalletOwner.GUEST).filter { it.value.id in ids }
        assertEquals(expectedIds, stored.map { it.value.id })
        assertTrue(stored.zipWithNext().all { (newer, older) -> newer.ledgerSequence > older.ledgerSequence })
        assertEquals(listOf(14100L, 14000L, 4000L), stored.map { it.value.balanceAfterCents })
    }

    @Test fun successiveRepositoryOperationsWithFixedClockUseCommitOrder() = runBlocking {
        val card = freshCard(10000)
        val first = value(repository.topUp(card.id, 10, "fixed-first"))
        val second = value(repository.pay(card.id, "campus-cafe", 20, "fixed-second"))
        val third = value(repository.topUp(card.id, 30, "fixed-third"))
        val relevant = setOf("fixed-first", "fixed-second", "fixed-third")
        val history = repository.transactions.first().filter { it.operationId in relevant }
        assertTrue(history.all { it.createdAt == now })
        assertEquals(listOf(third.transaction, second.transaction, first.transaction), history)
        assertEquals(listOf(10020L, 9990L, 10010L), history.map { it.balanceAfterCents })
        assertEquals(history, database.dao().allTransactions(WalletOwner.GUEST).map { it.value }.filter { it.operationId in relevant })
    }

    @Test fun lowBalanceAlertUsesFinalBalanceAndRespectsPreferences() = runBlocking {
        val card = freshCard(6000)
        value(repository.updatePreferences(repository.preferences.first().copy(autoReloadEnabled = true)))
        val topped = value(repository.pay(card.id, "campus-cafe", 2000, "no-alert-after-reload"))
        assertEquals(14000L, topped.finalBalanceCents)
        // A reload lifted the final balance, so no low-balance alert is stored; the payment is still
        // recorded in the inbox as a transaction row.
        assertEquals(listOf(MessageKind.PAYMENT),
            repository.messages.first().filter { it.transactionId == topped.transaction.id }.map { it.kind })
        value(repository.updatePreferences(repository.preferences.first().copy(autoReloadEnabled = false, lowBalanceAlerts = false)))
        val quiet = value(repository.pay(card.id, "campus-cafe", 14000, "alerts-disabled"))
        assertEquals(listOf(MessageKind.PAYMENT),
            repository.messages.first().filter { it.transactionId == quiet.transaction.id }.map { it.kind })
    }

    @Test fun rejectedOperationsLeaveLedgerBalanceAndMessagesUnchanged() = runBlocking {
        val card = freshCard(100)
        value(repository.updatePreferences(repository.preferences.first().copy(autoReloadEnabled = true)))
        val before = repository.transactions.first()
        val messages = repository.messages.first()
        assertError(WalletError.INSUFFICIENT_BALANCE, repository.pay(card.id, "campus-cafe", 101, "insufficient"))
        for (amount in listOf(0L, -1L, Long.MAX_VALUE)) {
            assertError(WalletError.INVALID_AMOUNT, repository.topUp(card.id, amount, "invalid-top-$amount"))
            assertError(WalletError.INVALID_AMOUNT, repository.pay(card.id, "campus-cafe", amount, "invalid-pay-$amount"))
        }
        assertError(WalletError.NOT_FOUND, repository.pay(card.id, "missing", 1, "unknown-merchant"))
        assertError(WalletError.INVALID_OPERATION_ID, repository.topUp(card.id, 1, ""))
        value(repository.setCardFrozen(card.id, true))
        assertError(WalletError.CARD_FROZEN, repository.topUp(card.id, 1, "frozen-top"))
        assertError(WalletError.CARD_FROZEN, repository.pay(card.id, "campus-cafe", 1, "frozen-pay"))
        assertEquals(100L, balance(card.id))
        assertEquals(before, repository.transactions.first())
        assertEquals(messages, repository.messages.first())
    }

    @Test fun reloadOverflowLeavesNoPartialDebit() = runBlocking {
        val card = freshCard(1000)
        value(repository.updatePreferences(repository.preferences.first().copy(
            autoReloadEnabled = true, autoReloadAmountCents = Money.MAX_BALANCE_CENTS)))
        val before = repository.transactions.first()
        assertError(WalletError.BALANCE_LIMIT, repository.pay(card.id, "campus-cafe", 100, "reload-overflow"))
        assertEquals(1000L, balance(card.id))
        assertEquals(before, repository.transactions.first())
        assertNull(repository.getReceipt("reload-overflow"))
    }

    @Test fun concurrentSameIdCreatesExactlyOnePayment() = runBlocking {
        val card = freshCard(10000)
        val results = coroutineScope {
            (1..20).map { async(Dispatchers.Default) { repository.pay(card.id, "campus-cafe", 1000, "shared-id") } }.awaitAll()
        }
        val receipts = results.map { value(it) }
        assertTrue(receipts.all { it == receipts.first() })
        assertEquals(9000L, balance(card.id))
        assertEquals(1, repository.transactions.first().count { it.operationId == "shared-id" })
    }

    @Test fun concurrentDifferentPaymentsCannotOverspend() = runBlocking {
        val card = freshCard(10000)
        val results = coroutineScope {
            listOf("first-racer", "second-racer").map { id ->
                async(Dispatchers.Default) { repository.pay(card.id, "campus-cafe", 6000, id) }
            }.awaitAll()
        }
        assertEquals(1, results.count { it is WalletResult.Success })
        assertEquals(1, results.count { it is WalletResult.Error && it.code == WalletError.INSUFFICIENT_BALANCE })
        assertEquals(4000L, balance(card.id))
    }

    @Test fun ledgerInsertFailureRollsBackEarlierBalanceWriteAndIdempotencyKey() = runBlocking {
        val card = freshCard(10000)
        val before = repository.transactions.first()
        sql("CREATE TRIGGER reject_ledger BEFORE INSERT ON transactions BEGIN SELECT RAISE(ABORT, 'Injected ledger failure'); END")
        assertError(WalletError.STORAGE_ERROR, repository.topUp(card.id, 500, "retry-after-storage"))
        assertEquals(10000L, balance(card.id))
        assertEquals(before, repository.transactions.first())
        assertNull(repository.getReceipt("retry-after-storage"))
        sql("DROP TRIGGER reject_ledger")
        assertEquals(10500L, value(repository.topUp(card.id, 500, "retry-after-storage")).finalBalanceCents)
    }

    @Test fun failureAfterReloadAndOperationWritesRollsBackAllRecords() = runBlocking {
        val card = freshCard(2000)
        value(repository.updatePreferences(repository.preferences.first().copy(autoReloadEnabled = true, autoReloadAmountCents = 100)))
        val before = repository.transactions.first()
        val messages = repository.messages.first()
        sql("CREATE TRIGGER reject_alert BEFORE INSERT ON messages WHEN NEW.type = 'LOW_BALANCE' BEGIN SELECT RAISE(ABORT, 'Injected alert failure'); END")
        assertError(WalletError.STORAGE_ERROR, repository.pay(card.id, "campus-cafe", 1500, "rollback-all"))
        assertEquals(2000L, balance(card.id))
        assertEquals(before, repository.transactions.first())
        assertEquals(messages, repository.messages.first())
        assertNull(repository.getReceipt("rollback-all"))
    }

    @Test fun passInsertFailureRollsBackDebitAndLedger() = runBlocking {
        val card = freshCard(50000)
        val before = repository.transactions.first()
        sql("CREATE TRIGGER reject_pass BEFORE INSERT ON passes BEGIN SELECT RAISE(ABORT, 'Injected pass failure'); END")
        assertError(WalletError.STORAGE_ERROR, repository.buyPass(card.id, "pass-failure"))
        assertEquals(50000L, balance(card.id))
        assertEquals(before, repository.transactions.first())
        assertTrue(repository.passes.first().isEmpty())
        assertNull(repository.getReceipt("pass-failure"))
    }

    @Test fun cancellationOfRoomTransactionRollsBackWrittenBalanceAndLedger() = runBlocking {
        val card = freshCard(10000)
        val before = repository.transactions.first()
        try {
            database.withTransaction {
                database.dao().updateCard(CardEntity(card.copy(balanceCents = 9900)))
                database.dao().insertTransaction(TransactionEntity(WalletTransaction("cancelled", "cancelled", card.id,
                    TransactionType.PAYMENT, 100, 9900, "campus-cafe", "Campus Cafe", Category.FOOD, now, "Cancelled test")))
                throw CancellationException("Cancelled after writes")
            }
        } catch (_: CancellationException) { /* Expected; Room must roll back. */ }
        assertEquals(10000L, balance(card.id))
        assertEquals(before, repository.transactions.first())
    }

    @Test fun cancellationAfterCommitRecoversOriginalReceiptWithoutAnotherDebit() = runBlocking {
        val card = freshCard(10000)
        val dispatcher = PausedDispatcher()
        val parent = SupervisorJob()
        val receivedCancellation = AtomicBoolean(false)
        val receivedResult = AtomicBoolean(false)
        val pending = CoroutineScope(parent + dispatcher).launch {
            try {
                repository.pay(card.id, "campus-cafe", 500, "late-cancellation")
                receivedResult.set(true)
            } catch (cancelled: CancellationException) {
                receivedCancellation.set(true)
                throw cancelled
            }
        }
        try {
            dispatcher.next().run() // Start on the caller dispatcher, then suspend into repository IO.
            val returnToCaller = dispatcher.next() // Queued only after the IO mutation has completed.
            val committed = repository.getReceipt("late-cancellation")
            assertNotNull("The response is paused after a real Room commit", committed)
            assertEquals(9500L, balance(card.id))
            pending.cancel()
            returnToCaller.run()
            pending.join()
            assertTrue(receivedCancellation.get())
            assertFalse(receivedResult.get())
            // An independent active caller reconciles the original request, never replacing its ID.
            assertEquals(committed, value(repository.pay(card.id, "campus-cafe", 500, "late-cancellation")))
            assertEquals(9500L, balance(card.id))
            assertEquals(1, repository.transactions.first().count { it.operationId == "late-cancellation" })
        } finally {
            parent.cancel()
            dispatcher.drain()
        }
    }

    @Test fun passPurchaseIsIdempotentAndPreventsAnOverlappingPass() = runBlocking {
        val card = freshCard(100000)
        val receipt = value(repository.buyPass(card.id, "pass"))
        val pass = receipt.pass!!
        assertEquals(70000L, balance(card.id))
        assertEquals(Instant.ofEpochMilli(now).atZone(ZoneId.of("Asia/Hong_Kong")).plusMonths(1).toInstant().toEpochMilli(), pass.validUntil)
        assertEquals(receipt, value(repository.buyPass(card.id, "pass")))
        assertError(WalletError.PASS_ALREADY_ACTIVE, repository.buyPass(card.id, "second-pass"))
        assertEquals(1, repository.passes.first().count { it.cardId == card.id })
        assertEquals(70000L, balance(card.id))
    }

    @Test fun couponsAndFavoritesPersistAndCannotBeRedeemedTwiceOrAfterExpiry() = runBlocking {
        val offer = repository.offers.first().first()
        value(repository.setOfferFavorite(offer.id, true))
        val redeemed = value(repository.redeemOffer(offer.id))
        assertTrue(redeemed.isFavorite)
        assertEquals(now, redeemed.redeemedAt)
        assertTrue(redeemed.couponCode!!.startsWith("DEMO-"))
        assertError(WalletError.ALREADY_REDEEMED, repository.redeemOffer(offer.id))
        value(repository.refreshDemoContent())
        assertEquals(redeemed, repository.offers.first().single { it.id == offer.id })
        val other = repository.offers.first().first { it.id != offer.id }
        val expiredRepository = RoomTapLinkRepository(database) { other.expiresAt }
        assertError(WalletError.OFFER_EXPIRED, expiredRepository.redeemOffer(other.id))
    }

    @Test fun remindersArePreferenceAwareAndDeduplicated() = runBlocking {
        val offer = repository.offers.first().first()
        value(repository.setOfferFavorite(offer.id, true))
        assertEquals(1, value(repository.generateReminders(now)))
        assertEquals(0, value(repository.generateReminders(now)))
        val reminder = repository.messages.first().single { it.type == MessageType.OFFER_EXPIRY }
        value(repository.markMessageRead(reminder.id))
        assertTrue(repository.messages.first().single { it.id == reminder.id }.isRead)
        value(repository.markAllMessagesRead())
        assertTrue(repository.messages.first().all { it.isRead })
        value(repository.updatePreferences(repository.preferences.first().copy(remindersEnabled = false)))
        assertEquals(0, value(repository.generateReminders(now)))
    }

    @Test fun fictionalSubsidyIsClaimedOncePerCompletedMonthWithMatchingCredit() = runBlocking {
        val month = YearMonth.of(2026, 10).toString()
        val before = balance("card-commute")
        val receipt = value(repository.claimSubsidy("card-commute", month, "subsidy"))
        assertEquals(3000L, receipt.transaction.amountCents)
        assertEquals(before + 3000L, balance("card-commute"))
        assertEquals(receipt, value(repository.claimSubsidy("card-commute", month, "subsidy")))
        assertError(WalletError.ALREADY_CLAIMED, repository.claimSubsidy("card-commute", month, "second-subsidy"))
        assertError(WalletError.INVALID_MONTH, repository.claimSubsidy("card-commute", "2026-11", "current-month"))
        assertError(WalletError.INVALID_MONTH, repository.claimSubsidy("card-commute", "invalid", "invalid-month"))
        assertEquals(1, repository.subsidyClaims.first().size)
    }

    @Test fun databaseReopenPreservesCardsPreferencesLedgerCouponsAndRetryReceipt() = runBlocking {
        val name = "taplink-persistence-${UUID.randomUUID()}.db"
        var disk = Room.databaseBuilder(context, TapLinkDatabase::class.java, name).build()
        try {
            val first = RoomTapLinkRepository(disk) { now }
            first.initialize()
            val card = value(first.createCard("Persisted"))
            value(first.selectCard(card.id))
            value(first.updatePreferences(first.preferences.first().copy(theme = ThemeMode.DARK, widgetBalanceVisible = true)))
            val receipt = value(first.topUp(card.id, 12345, "disk-topup"))
            val offer = value(first.redeemOffer(first.offers.first().first().id))
            disk.close()
            disk = Room.databaseBuilder(context, TapLinkDatabase::class.java, name).build()
            val reopened = RoomTapLinkRepository(disk) { now }
            assertEquals(4, reopened.cards.first().size)
            assertEquals(12345L, reopened.selectedCard.first()!!.balanceCents)
            assertEquals(ThemeMode.DARK, reopened.preferences.first().theme)
            assertTrue(reopened.preferences.first().widgetBalanceVisible)
            assertEquals(receipt, value(reopened.topUp(card.id, 12345, "disk-topup")))
            assertEquals(offer, reopened.offers.first().single { it.id == offer.id })
            value(reopened.topUp(card.id, 1, "disk-next"))
            assertEquals(listOf("disk-next", "disk-topup"), reopened.transactions.first()
                .filter { it.cardId == card.id }.map { it.operationId })
        } finally {
            disk.close()
            context.deleteDatabase(name)
        }
    }

    private suspend fun freshCard(balance: Long): Card {
        val card = value(repository.createCard("Test card"))
        value(repository.topUp(card.id, balance, "fund-${card.id}"))
        return repository.cards.first().single { it.id == card.id }
    }

    private suspend fun balance(cardId: String): Long = repository.cards.first().single { it.id == cardId }.balanceCents

    private suspend fun sql(statement: String) = withContext(Dispatchers.IO) {
        database.openHelper.writableDatabase.execSQL(statement)
    }

    /**
     * The reported defect: signing in as another account showed the previous wallet. Each owner now
     * has its own cards, ledger and messages, and a card id from another owner is not found.
     */
    @Test fun eachOwnerSeesOnlyItsOwnWallet() = runBlocking {
        val guestCards = repository.cards.first()
        assertEquals(3, guestCards.size)

        repository.useOwner("account-a")
        repository.initialize()
        val aCards = repository.cards.first()
        assertEquals(1, aCards.size)
        assertEquals("My TapLink Card", aCards.single().name)
        assertEquals(0L, aCards.single().balanceCents)
        assertTrue(repository.transactions.first().isEmpty())
        value(repository.topUp(aCards.single().id, 5000, "account-a-top-up"))
        assertEquals(5000L, repository.cards.first().single().balanceCents)

        repository.useOwner("account-b")
        repository.initialize()
        assertEquals(1, repository.cards.first().size)
        assertEquals(0L, repository.cards.first().single().balanceCents)
        assertTrue(repository.transactions.first().isEmpty())
        // A card id belonging to another owner must not be spendable.
        assertError(WalletError.NOT_FOUND, repository.topUp(aCards.single().id, 1000, "cross-owner-top-up"))

        repository.useOwner(WalletOwner.GUEST)
        repository.initialize()
        assertEquals(guestCards.map { it.id }, repository.cards.first().map { it.id })
        assertEquals(guestCards.map { it.balanceCents }, repository.cards.first().map { it.balanceCents })
    }

    private fun <T> value(result: WalletResult<T>): T = when (result) {
        is WalletResult.Success -> result.value
        is WalletResult.Error -> throw AssertionError("${result.code}: ${result.message}")
    }

    private fun assertError(code: WalletError, result: WalletResult<*>) {
        assertTrue("Expected $code, got $result", result is WalletResult.Error)
        assertEquals(code, (result as WalletResult.Error).code)
    }

    /** Only the test caller is paused; Room's transaction executor and IO dispatcher remain live. */
    private class PausedDispatcher : CoroutineDispatcher() {
        private val continuations = LinkedBlockingQueue<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { continuations.add(block) }
        fun next(): Runnable = continuations.poll(10, TimeUnit.SECONDS)
            ?: throw AssertionError("Timed out waiting for a caller continuation")
        fun drain() {
            while (true) (continuations.poll() ?: return).run()
        }
    }
}
