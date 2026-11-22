package com.taplink.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taplink.app.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class WalletViewModel(val repository: TapLinkRepository, private val saved: SavedStateHandle) : ViewModel() {
    private val sharing = SharingStarted.WhileSubscribed(5_000)
    val cards = repository.cards.stateIn(viewModelScope, sharing, emptyList())
    val selectedCard = repository.selectedCard.stateIn(viewModelScope, sharing, null)
    val transactions = repository.transactions.stateIn(viewModelScope, sharing, emptyList())
    val merchants = repository.merchants.stateIn(viewModelScope, sharing, emptyList())
    val locations = repository.locations.stateIn(viewModelScope, sharing, emptyList())
    val preferences = repository.preferences.stateIn(viewModelScope, sharing, AppPreferences())
    val messages = repository.messages.stateIn(viewModelScope, sharing, emptyList())
    val offers = repository.offers.stateIn(viewModelScope, sharing, emptyList())
    val passes = repository.passes.stateIn(viewModelScope, sharing, emptyList())
    val claims = repository.subsidyClaims.stateIn(viewModelScope, sharing, emptyList())
    val route = saved.getStateFlow("route", "Home")
    val ready = MutableStateFlow(false)
    val loadError = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val feedback = MutableStateFlow<String?>(null)
    val receipt = MutableStateFlow<Receipt?>(null)
    val receiptLoading = MutableStateFlow(false)
    val receiptError = MutableStateFlow<String?>(null)
    val operationError = saved.getStateFlow("operationError", "")
    val operationState = saved.getStateFlow("operationState", "DRAFT")
    val cardDialog = saved.getStateFlow("cardDialog", "")
    val cardDialogName = saved.getStateFlow("cardDialogName", "")
    val offerDialog = saved.getStateFlow("offerDialog", "")
    val locationLatitude = saved.getStateFlow("locationLatitude", "")
    val locationLongitude = saved.getStateFlow("locationLongitude", "")
    val locationNote = saved.getStateFlow("locationNote", "Choose a district, or use your approximate location.")
    val locating = MutableStateFlow(false)
    private var locationGeneration = 0L
    private var receiptLookup: Job? = null
    private var receiptGeneration = 0L
    init { initialize() }

    fun text(key: String, default: String = "") = saved.getStateFlow(key, default)
    fun set(key: String, value: String) {
        // Uncertain attempts must keep their exact fingerprint as well as their UUID.
        if (hasUnresolvedOperation() && key in operationInputKeys) return
        saved[key] = value
    }
    fun value(key: String) = saved.get<String>(key).orEmpty()
    fun initialize() {
        viewModelScope.launch {
            loadError.value = null
            try {
                repository.initialize()
                if (hasUnresolvedOperation()) {
                    busy.value = true
                    try { reconcileOutcome(value("operationId")) }
                    finally { busy.value = false }
                    if (hasUnresolvedOperation()) { invalidateReceiptLookup(); saved["route"] = "Review"; saved["backStack"] = "Home|" }
                }
                ready.value = true
                if (receipt.value == null) loadCurrentReceipt()
            }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { loadError.value = "Your wallet could not be loaded. Please try again." }
        }
    }
    private fun invalidateReceiptLookup() {
        receiptGeneration++
        receiptLookup?.cancel()
        receiptLookup = null
        receiptLoading.value = false
        receiptError.value = null
        receipt.value = null
    }
    /** Both cancellation and an identity check are required: a provider may finish after cancellation. */
    fun loadCurrentReceipt() {
        val targetRoute = route.value
        if (targetRoute != "Transaction" && targetRoute != "Receipt") return
        val key = if (targetRoute == "Transaction") "detailOperation" else "receiptOperation"
        val id = value(key).takeIf { it.isNotBlank() } ?: return
        invalidateReceiptLookup()
        val generation = receiptGeneration
        receiptLoading.value = true
        receiptLookup = viewModelScope.launch {
            fun isCurrent() = generation == receiptGeneration && route.value == targetRoute && value(key) == id
            try {
                val loaded = repository.getReceipt(id).matchingOperation(id)
                if (isCurrent()) {
                    receipt.value = loaded
                    if (loaded == null) receiptError.value = "This receipt could not be found. Your transaction history is still available."
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (isCurrent()) receiptError.value = "The receipt could not be loaded. Please try again." }
            finally { if (isCurrent()) receiptLoading.value = false }
        }
    }
    fun navigate(destination: String) {
        if (busy.value) return
        if (destination != "Explore") cancelLocationRequest()
        invalidateReceiptLookup()
        saved["backStack"] = value("backStack") + route.value + "|"
        saved["route"] = destination
        loadCurrentReceipt()
    }
    fun root(destination: String) {
        if (busy.value) return
        if (destination != "Explore") cancelLocationRequest()
        invalidateReceiptLookup()
        saved["backStack"] = ""
        saved["route"] = destination
        loadCurrentReceipt()
    }
    fun back() {
        if (busy.value) return
        cancelLocationRequest()
        invalidateReceiptLookup()
        val stack = value("backStack").split('|').filter { it.isNotEmpty() }
        // Leave uncertain requests intact; never return to an editable form for that attempt.
        saved["route"] = if (hasUnresolvedOperation() && route.value == "Review") "Home" else stack.lastOrNull() ?: "Home"
        saved["backStack"] = if (route.value == "Home") "" else stack.dropLast(1).joinToString("|", postfix = if (stack.size > 1) "|" else "")
        loadCurrentReceipt()
    }
    fun startOperation(kind: String, cardId: String? = null, month: String = "", amountCents: Long? = null) {
        if (busy.value) return
        if (hasUnresolvedOperation()) {
            feedback.value = "Resolve your previous request before starting another transaction. Its details and request ID have been kept."
            navigate("Review")
            return
        }
        invalidateReceiptLookup()
        set("operationState", "DRAFT")
        set("operationKind", kind)
        set("operationCard", cardId ?: selectedCard.value?.id.orEmpty())
        set("operationMerchant", "")
        set("operationAmount", when { kind == "PASS" -> amountInput(PassCatalog.MONTHLY_PASS_PRICE_CENTS); amountCents != null -> amountInput(amountCents); else -> "" })
        set("operationMonth", month)
        set("operationId", "")
        set("operationError", "")
        receipt.value = null
        navigate(if (kind == "PASS" || kind == "SUBSIDY") "Review" else "Form")
        if (kind == "PASS" || kind == "SUBSIDY") { set("operationId", UUID.randomUUID().toString()); set("operationState", "REVIEW") }
    }
    fun review() {
        if (busy.value) return
        if (hasUnresolvedOperation()) { navigate("Review"); return }
        val amount = Money.parseCents(value("operationAmount"))
        val card = cards.value.find { it.id == value("operationCard") }
        val error = when {
            card == null -> "Choose a card to continue."
            card.isFrozen -> "This card is frozen. Unfreeze it from Home first."
            amount == null -> "Enter a positive HKD amount with at most two decimal places."
            value("operationKind") == "PAYMENT" && value("operationMerchant").isBlank() -> "Choose a merchant to continue."
            value("operationKind") == "PAYMENT" && amount > card.balanceCents -> "This card has insufficient balance. Top up or choose another card."
            else -> ""
        }
        set("operationError", error)
        if (error.isNotEmpty()) return
        set("operationId", UUID.randomUUID().toString())
        set("operationState", "REVIEW")
        navigate("Review")
    }
    fun confirm() {
        if (busy.value || route.value != "Review" || operationState.value == "COMPLETE") return
        val id = value("operationId").ifBlank { UUID.randomUUID().toString().also { set("operationId", it) } }
        val card = value("operationCard")
        val merchant = value("operationMerchant")
        val amount = Money.parseCents(value("operationAmount")) ?: 0L
        val kind = value("operationKind")
        val month = value("operationMonth")
        busy.value = true
        set("operationError", "")
        set("operationState", "PENDING")
        viewModelScope.launch {
            try {
                val result = when (kind) {
                    "TOPUP" -> repository.topUp(card, amount, id)
                    "PAYMENT" -> repository.pay(card, merchant, amount, id)
                    "PASS" -> repository.buyPass(card, id)
                    "SUBSIDY" -> repository.claimSubsidy(card, month, id)
                    else -> WalletResult.Error(WalletError.INVALID_AMOUNT, "Please start a new transaction.")
                }
                when (result) {
                    is WalletResult.Error -> { set("operationState", "REJECTED"); set("operationError", result.message) }
                    is WalletResult.Success -> {
                        if (!completeOperation(id, result.value)) reconcileOutcome(id)
                    }
                }
            } catch (e: CancellationException) {
                // Cancellation can arrive after commit. Only a bounded read is non-cancellable;
                // the financial mutation is never repeated implicitly.
                set("operationState", "UNCERTAIN")
                reconcileOutcome(id)
                throw e
            }
            catch (_: Exception) { set("operationState", "UNCERTAIN"); reconcileOutcome(id) }
            finally { busy.value = false }
        }
    }
    private fun hasUnresolvedOperation() = value("operationId").isNotBlank() &&
        operationState.value in setOf("PENDING", "UNCERTAIN")

    private fun completeOperation(id: String, result: Receipt): Boolean {
        if (value("operationId") != id || result.transaction.operationId != id) return false
        invalidateReceiptLookup()
        set("receiptOperation", id)
        set("operationState", "COMPLETE")
        set("operationError", "")
        receipt.value = result
        // A receipt cannot navigate back into a completed confirmation.
        saved["backStack"] = "Home|"
        saved["route"] = "Receipt"
        return true
    }

    private suspend fun reconcileOutcome(id: String) {
        if (id.isBlank() || value("operationId") != id) return
        set("operationState", "UNCERTAIN")
        val stored = withContext(NonCancellable) {
            try { withTimeoutOrNull(3_000) { repository.getReceipt(id) } }
            catch (_: Exception) { null }
        }
        if (value("operationId") != id) return
        if (stored != null && completeOperation(id, stored)) return
        set("operationError", "The outcome is not confirmed. Check for a saved receipt or retry this same request. Your original details and request ID are retained.")
    }

    fun checkOperationOutcome() {
        if (busy.value || !hasUnresolvedOperation()) return
        busy.value = true
        viewModelScope.launch { try { reconcileOutcome(value("operationId")) } finally { busy.value = false } }
    }
    fun <T> act(success: String? = null, action: suspend () -> WalletResult<T>, after: (T) -> Unit = {}) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                when (val result = action()) {
                    is WalletResult.Error -> feedback.value = result.message
                    is WalletResult.Success -> { after(result.value); if (success != null) feedback.value = success }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { feedback.value = "That change could not be saved. Please try again." }
            finally { busy.value = false }
        }
    }
    fun updatePreferences(success: String? = null, change: (AppPreferences) -> AppPreferences) =
        act(success, action = { repository.transformPreferences(change) })

    fun openCardDialog(action: String, card: Card? = null) {
        if (busy.value) return
        set("cardDialogToken", UUID.randomUUID().toString())
        set("cardDialogTarget", card?.id.orEmpty())
        set("cardDialogName", if (action == "rename") card?.name.orEmpty() else "")
        set("cardDialog", action)
    }
    fun editCardDialogName(name: String) { if (!busy.value) set("cardDialogName", name.take(40)) }
    fun dismissCardDialog() { if (!busy.value) { set("cardDialog", ""); set("cardDialogToken", "") } }
    fun submitCardDialog() {
        if (busy.value) return
        val token = value("cardDialogToken").takeIf { it.isNotBlank() } ?: return
        val action = cardDialog.value
        val target = value("cardDialogTarget")
        val name = cardDialogName.value.trim()
        fun closeCurrent() {
            if (value("cardDialogToken") == token) {
                set("cardDialog", ""); set("cardDialogToken", ""); set("cardDialogName", "")
            }
        }
        when (action) {
            "create" -> act("Card created", { repository.createCard(name) }) { closeCurrent() }
            "rename" -> act("Card renamed", { repository.renameCard(target, name) }) { closeCurrent() }
            "freeze", "unfreeze" -> act("Card updated", { repository.setCardFrozen(target, action == "freeze") }) { closeCurrent() }
        }
    }
    fun openOfferDialog(offerId: String) {
        if (busy.value) return
        set("offerDialogToken", UUID.randomUUID().toString())
        set("offerDialog", offerId)
    }
    fun dismissOfferDialog() { if (!busy.value) { set("offerDialog", ""); set("offerDialogToken", "") } }
    fun submitOfferDialog() {
        if (busy.value) return
        val id = offerDialog.value.takeIf { it.isNotBlank() } ?: return
        val token = value("offerDialogToken")
        act("Demo coupon redeemed", { repository.redeemOffer(id) }) {
            if (value("offerDialogToken") == token) { set("offerDialog", ""); set("offerDialogToken", "") }
        }
    }

    fun requestLocation(request: ((Double?, Double?, String?) -> Unit) -> Unit) {
        if (route.value != "Explore") return
        val generation = ++locationGeneration
        locating.value = true
        set("locationNote", "Finding an approximate location…")
        try {
            request { lat, lon, reason ->
                if (generation == locationGeneration && route.value == "Explore") {
                    val valid = lat != null && lon != null && lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
                    set("locationLatitude", if (valid) lat.toString() else "")
                    set("locationLongitude", if (valid) lon.toString() else "")
                    locating.value = false
                    set("locationNote", reason ?: if (valid) "Approximate location only. Distances are straight-line estimates." else "Location unavailable. Select a district above.")
                }
            }
        } catch (_: Exception) {
            cancelLocationRequest()
            set("locationNote", "Location is unavailable. Choose a district to continue.")
        }
    }
    fun cancelLocationRequest() {
        locationGeneration++
        if (locating.value) set("locationNote", "Location request stopped. Choose a district or try again.")
        locating.value = false
    }
    fun selectDistrict(district: String) {
        if (busy.value) return
        cancelLocationRequest()
        set("locationLatitude", ""); set("locationLongitude", "")
        set("locationNote", "Manual location mode. Showing the selected district above.")
        updatePreferences { it.copy(manualDistrict = district) }
    }
    fun openTransaction(transaction: WalletTransaction) {
        if (busy.value) return
        set("detailTransaction", transaction.id)
        set("detailOperation", transaction.operationId)
        navigate("Transaction")
    }

    private companion object {
        val operationInputKeys = setOf("operationKind", "operationCard", "operationMerchant", "operationAmount", "operationMonth", "operationId")
    }
}

internal fun Receipt?.matchingOperation(operationId: String): Receipt? =
    this?.takeIf { operationId.isNotBlank() && it.transaction.operationId == operationId }
