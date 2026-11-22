package com.taplink.app.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.taplink.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Adversarial scheduling tests: observations intentionally lag successful writes. */
@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModelTest {
    @get:Rule val instantTasks = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val models = mutableListOf<WalletViewModel>()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { models.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain() }

    private fun TestScope.model(repo: DelayedRepository, saved: SavedStateHandle = SavedStateHandle()): WalletViewModel {
        val vm = WalletViewModel(repo, saved).also { models += it }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.cards.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.preferences.collect() }
        runCurrent()
        return vm
    }
    private fun prepareTopUp(vm: WalletViewModel, card: String = "B"): String {
        vm.startOperation("TOPUP", card)
        vm.set("operationAmount", "50.00")
        vm.review()
        assertEquals("Review", vm.route.value)
        return vm.value("operationId")
    }

    @Test fun selectingCardThenChangingPreferencesPreservesTheCommittedSelectionWithoutAnEmission() = runTest(dispatcher) {
        val repo = DelayedRepository()
        val vm = model(repo)
        vm.act(action = { repo.selectCard("B") }); runCurrent()
        assertEquals("A", vm.preferences.value.selectedCardId) // deliberately held observation
        vm.updatePreferences { it.copy(balanceVisible = false) }; runCurrent()
        assertEquals("B", repo.committed.selectedCardId)
        assertFalse(repo.committed.balanceVisible)
        assertEquals(0, repo.snapshotWrites)
    }

    @Test fun independentPrivacyReminderAndAmountChangesSurviveDelayedEmissions() = runTest(dispatcher) {
        val repo = DelayedRepository()
        val vm = model(repo)
        vm.updatePreferences { it.copy(widgetBalanceVisible = false) }; runCurrent()
        vm.updatePreferences { it.copy(remindersEnabled = false) }; runCurrent()
        vm.updatePreferences("Amounts saved") { it.copy(lowBalanceThresholdCents = 1700, autoReloadAmountCents = 8000) }; runCurrent()
        assertTrue(vm.preferences.value.widgetBalanceVisible) // UI observation is still old
        assertFalse(repo.committed.widgetBalanceVisible)
        assertFalse(repo.committed.remindersEnabled)
        assertEquals(1700L, repo.committed.lowBalanceThresholdCents)
        assertEquals(8000L, repo.committed.autoReloadAmountCents)
        assertEquals(0, repo.snapshotWrites)
    }

    @Test fun oldDetailCompletionCannotReplaceANewerSuccessfulReceipt() = runTest(dispatcher) {
        val repo = DelayedRepository()
        val old = fixtureReceipt("old", "A", 1234)
        val held = CompletableDeferred<Receipt?>()
        repo.readReceipt = { id -> if (id == "old") withContext(NonCancellable) { held.await() } else repo.receipts[id] }
        val vm = model(repo)
        vm.openTransaction(old.transaction); runCurrent()
        vm.back()
        val newId = prepareTopUp(vm)
        vm.confirm(); runCurrent()
        assertEquals(newId, vm.receipt.value?.transaction?.operationId)
        held.complete(old); runCurrent()
        assertEquals("Receipt", vm.route.value)
        assertEquals(newId, vm.receipt.value?.transaction?.operationId)
        assertEquals("B", vm.receipt.value?.transaction?.cardId)
        assertEquals(5000L, vm.receipt.value?.transaction?.amountCents)
    }

    @Test fun reversedDetailLookupsKeepTheCurrentlyRequestedReceipt() = runTest(dispatcher) {
        val repo = DelayedRepository()
        val a = fixtureReceipt("a", "A", 100)
        val b = fixtureReceipt("b", "B", 200)
        val held = CompletableDeferred<Receipt?>()
        repo.readReceipt = { id -> if (id == "a") withContext(NonCancellable) { held.await() } else b }
        val vm = model(repo)
        vm.openTransaction(a.transaction); runCurrent()
        vm.back(); vm.openTransaction(b.transaction); runCurrent()
        held.complete(a); runCurrent()
        assertEquals("b", vm.value("detailOperation"))
        assertEquals(b, vm.receipt.value)
        assertFalse(vm.receiptLoading.value)
    }

    @Test fun delayedRestorationCannotOverwriteANewOperation() = runTest(dispatcher) {
        val repo = DelayedRepository()
        val old = fixtureReceipt("restore-old", "A", 100)
        val held = CompletableDeferred<Receipt?>()
        repo.readReceipt = { id -> if (id == "restore-old") withContext(NonCancellable) { held.await() } else repo.receipts[id] }
        val vm = model(repo, SavedStateHandle(mapOf("route" to "Receipt", "receiptOperation" to "restore-old")))
        vm.root("Home")
        val newId = prepareTopUp(vm)
        vm.confirm(); runCurrent()
        held.complete(old); runCurrent()
        assertEquals(newId, vm.receipt.value?.transaction?.operationId)
    }

    @Test fun successRenderingRejectsAReceiptWithTheWrongOperationIdentity() {
        val receipt = fixtureReceipt("stored", "A", 5000)
        assertNull(receipt.matchingOperation("other"))
        assertNull(receipt.matchingOperation(""))
        assertSame(receipt, receipt.matchingOperation("stored"))
    }

    @Test fun cancellationAfterCommitReconcilesWithoutSubmittingAgain() = runTest(dispatcher) {
        val repo = DelayedRepository()
        repo.performTopUp = { card, amount, id ->
            repo.receipts[id] = fixtureReceipt(id, card, amount)
            throw CancellationException("Result delivery was cancelled after commit")
        }
        val vm = model(repo)
        val id = prepareTopUp(vm)
        vm.confirm(); runCurrent()
        assertEquals("Receipt", vm.route.value)
        assertEquals(id, vm.receipt.value?.transaction?.operationId)
        assertEquals(id, vm.value("operationId"))
        assertEquals(1, repo.topUpIds.size)
        assertFalse(vm.busy.value)
    }

    @Test fun unresolvedCancellationPreservesFingerprintAndUuidUntilSameRequestRetry() = runTest(dispatcher) {
        val repo = DelayedRepository()
        repo.performTopUp = { _, _, _ -> throw CancellationException("No outcome delivered") }
        val vm = model(repo)
        val id = prepareTopUp(vm)
        vm.confirm(); runCurrent()
        assertEquals("UNCERTAIN", vm.operationState.value)
        vm.back()
        vm.startOperation("PAYMENT", "A") // cannot replace the unresolved intent
        vm.set("operationAmount", "99.99") // cannot change its fingerprint either
        assertEquals("Review", vm.route.value)
        assertEquals(id, vm.value("operationId"))
        assertEquals("TOPUP", vm.value("operationKind"))
        assertEquals("50.00", vm.value("operationAmount"))
        assertEquals("B", vm.value("operationCard"))
        repo.performTopUp = { card, amount, request -> WalletResult.Success(fixtureReceipt(request, card, amount)) }
        vm.confirm(); vm.confirm(); runCurrent()
        assertEquals(listOf(id, id), repo.topUpIds)
        assertEquals("Receipt", vm.route.value)
        assertEquals(id, vm.receipt.value?.transaction?.operationId)
    }

    @Test fun restoredPendingRequestFindsItsCommittedReceiptWithoutAnotherMutation() = runTest(dispatcher) {
        val repo = DelayedRepository()
        repo.receipts["pending-id"] = fixtureReceipt("pending-id", "B", 5000)
        val saved = SavedStateHandle(mapOf("route" to "Review", "operationId" to "pending-id", "operationState" to "PENDING", "operationKind" to "TOPUP", "operationCard" to "B", "operationAmount" to "50.00"))
        val vm = model(repo, saved)
        assertEquals("Receipt", vm.route.value)
        assertEquals("pending-id", vm.receipt.value?.transaction?.operationId)
        assertTrue(repo.topUpIds.isEmpty())
    }

    @Test fun cardCreationCompletionClosesStateObservedByAReplacementCollector() = runTest(dispatcher) {
        val repo = DelayedRepository()
        val held = CompletableDeferred<WalletResult<Card>>()
        repo.createResult = held
        val vm = model(repo)
        vm.openCardDialog("create"); vm.editCardDialogName("Campus")
        val oldScreen = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.cardDialog.collect() }
        vm.submitCardDialog(); runCurrent()
        oldScreen.cancel() // recreated composition subscribes to the retained VM
        var replacementDialog = ""
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.cardDialog.collect { replacementDialog = it } }
        assertEquals("create", replacementDialog)
        held.complete(WalletResult.Success(repo.cardA.copy(id = "created", name = "Campus"))); runCurrent()
        assertEquals("", replacementDialog)
        assertEquals("", vm.cardDialogName.value)
        vm.submitCardDialog(); runCurrent()
        assertEquals(1, repo.createCalls)
    }

    @Test fun cardCreationFailureKeepsTheCurrentDraftForCorrection() = runTest(dispatcher) {
        val repo = DelayedRepository()
        repo.createResult = CompletableDeferred(WalletResult.Error(WalletError.INVALID_NAME, "Invalid name"))
        val vm = model(repo)
        vm.openCardDialog("create"); vm.editCardDialogName("Campus"); vm.submitCardDialog(); runCurrent()
        assertEquals("create", vm.cardDialog.value)
        assertEquals("Campus", vm.cardDialogName.value)
    }

    @Test fun redemptionCompletionClosesRestoredDialogAndCannotBeResubmitted() = runTest(dispatcher) {
        val repo = DelayedRepository()
        val held = CompletableDeferred<WalletResult<Offer>>()
        repo.redeemResult = held
        val vm = model(repo)
        vm.openOfferDialog("offer"); vm.submitOfferDialog(); runCurrent()
        var currentDialog = ""
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.offerDialog.collect { currentDialog = it } }
        assertEquals("offer", currentDialog)
        held.complete(WalletResult.Success(Offer("offer", "merchant", "Demo", "", "", Long.MAX_VALUE, redeemedAt = 1, couponCode = "DEMO"))); runCurrent()
        assertEquals("", currentDialog)
        vm.submitOfferDialog(); runCurrent()
        assertEquals(1, repo.redeemCalls)
    }

    @Test fun manualDistrictChoiceWinsOverAnOlderLocationFix() = runTest(dispatcher) {
        val repo = DelayedRepository()
        val vm = model(repo)
        vm.root("Explore")
        lateinit var callback: (Double?, Double?, String?) -> Unit
        vm.requestLocation { callback = it }
        vm.selectDistrict("Central"); runCurrent()
        callback(22.3, 114.2, "Old location")
        assertEquals("Central", repo.committed.manualDistrict)
        assertEquals("", vm.locationLatitude.value)
        assertEquals("", vm.locationLongitude.value)
        assertFalse(vm.locating.value)
        assertNotEquals("Old location", vm.locationNote.value)
    }

    @Test fun leavingAndReenteringExploreInvalidatesTheOldLocationCallback() = runTest(dispatcher) {
        val vm = model(DelayedRepository())
        vm.root("Explore")
        lateinit var oldCallback: (Double?, Double?, String?) -> Unit
        vm.requestLocation { oldCallback = it }
        vm.root("Home"); vm.root("Explore")
        lateinit var newCallback: (Double?, Double?, String?) -> Unit
        vm.requestLocation { newCallback = it }
        oldCallback(22.1, 114.1, "Old")
        assertTrue(vm.locating.value)
        assertEquals("", vm.locationLatitude.value)
        newCallback(22.4, 114.3, "Current")
        assertEquals("22.4", vm.locationLatitude.value)
        assertEquals("Current", vm.locationNote.value)
        assertFalse(vm.locating.value)
    }
}

private fun fixtureReceipt(id: String, card: String, amount: Long): Receipt = Receipt(
    WalletTransaction("tx-$id", id, card, TransactionType.TOP_UP, amount, 10000 + amount, null, "Demo source", Category.OTHER, 1, "Simulated"), null, 10000 + amount,
)

/** Writes update committed state but never automatically emit preferences, reproducing Room lag. */
private class DelayedRepository : TapLinkRepository {
    val cardA = Card("A", "Card A", "•••• 0001", 10000, false, 1, "#1266D6")
    private val cardB = cardA.copy(id = "B", name = "Card B")
    var committed = AppPreferences(selectedCardId = "A", widgetBalanceVisible = true)
    override val preferences = MutableStateFlow(committed)
    override val cards = MutableStateFlow(listOf(cardA, cardB))
    override val selectedCard = MutableStateFlow<Card?>(cardA)
    override val transactions = MutableStateFlow(emptyList<WalletTransaction>())
    override val merchants = MutableStateFlow(emptyList<Merchant>())
    override val locations = MutableStateFlow(emptyList<ServiceLocation>())
    override val messages = MutableStateFlow(emptyList<WalletMessage>())
    override val passes = MutableStateFlow(emptyList<TransportPass>())
    override val offers = MutableStateFlow(emptyList<Offer>())
    override val subsidyClaims = MutableStateFlow(emptyList<SubsidyClaim>())
    val receipts = mutableMapOf<String, Receipt>()
    val topUpIds = mutableListOf<String>()
    var snapshotWrites = 0
    var createCalls = 0
    var redeemCalls = 0
    var createResult: CompletableDeferred<WalletResult<Card>>? = null
    var redeemResult: CompletableDeferred<WalletResult<Offer>>? = null
    var readReceipt: suspend (String) -> Receipt? = { receipts[it] }
    var performTopUp: suspend (String, Long, String) -> WalletResult<Receipt> = { card, amount, id ->
        WalletResult.Success(receipts.getOrPut(id) { fixtureReceipt(id, card, amount) })
    }
    override suspend fun initialize() = Unit
    override suspend fun selectCard(cardId: String): WalletResult<Unit> { committed = committed.copy(selectedCardId = cardId); return WalletResult.Success(Unit) }
    override suspend fun transformPreferences(change: (AppPreferences) -> AppPreferences): WalletResult<Unit> { committed = change(committed); return WalletResult.Success(Unit) }
    override suspend fun updatePreferences(preferences: AppPreferences): WalletResult<Unit> { snapshotWrites++; committed = preferences; return WalletResult.Success(Unit) }
    override suspend fun getReceipt(operationId: String) = readReceipt(operationId)
    override suspend fun topUp(cardId: String, amountCents: Long, operationId: String): WalletResult<Receipt> { topUpIds += operationId; return performTopUp(cardId, amountCents, operationId) }
    override suspend fun createCard(name: String): WalletResult<Card> { createCalls++; return createResult?.await() ?: WalletResult.Success(cardA.copy(name = name)) }
    override suspend fun redeemOffer(offerId: String): WalletResult<Offer> { redeemCalls++; return checkNotNull(redeemResult).await() }
    override fun observeTransactions(filter: TransactionFilter): Flow<List<WalletTransaction>> = transactions.map { WalletAnalytics.filter(it, filter) }
    override suspend fun renameCard(cardId: String, name: String): WalletResult<Unit> = error("Not used")
    override suspend fun setCardFrozen(cardId: String, frozen: Boolean): WalletResult<Unit> = error("Not used")
    override suspend fun pay(cardId: String, merchantId: String, amountCents: Long, operationId: String): WalletResult<Receipt> = error("Not used")
    override suspend fun buyPass(cardId: String, operationId: String): WalletResult<Receipt> = error("Not used")
    override suspend fun markMessageRead(messageId: String): WalletResult<Unit> = error("Not used")
    override suspend fun markAllMessagesRead(): WalletResult<Unit> = error("Not used")
    override suspend fun setOfferFavorite(offerId: String, favorite: Boolean): WalletResult<Unit> = error("Not used")
    override suspend fun claimSubsidy(cardId: String, month: String, operationId: String): WalletResult<Receipt> = error("Not used")
    override suspend fun refreshDemoContent(): WalletResult<Unit> = error("Not used")
    override suspend fun generateReminders(now: Long): WalletResult<Int> = error("Not used")
}
