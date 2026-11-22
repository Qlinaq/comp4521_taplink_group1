package com.taplink.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taplink.app.domain.*

@Composable
fun OperationForm(vm: WalletViewModel, platform: PlatformActions) {
    val kind by vm.text("operationKind").collectAsStateWithLifecycle()
    val cardId by vm.text("operationCard").collectAsStateWithLifecycle()
    val merchantId by vm.text("operationMerchant").collectAsStateWithLifecycle()
    val amount by vm.text("operationAmount").collectAsStateWithLifecycle()
    val error by vm.operationError.collectAsStateWithLifecycle()
    val cards by vm.cards.collectAsStateWithLifecycle()
    val merchants by vm.merchants.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val topUp = kind == "TOPUP"
    ScreenColumn {
        PageHeading("01 / Enter details", if (topUp) "A boost for your day." else "Make your next tap.", if (topUp) "Add simulated HKD from the TapLink demo source." else "Choose a demo merchant and enter your amount.")
        Panel {
            ChoiceField("Pay with / top up", cardId, cards.map { it.id to "${it.name} · ${if (it.isFrozen) "Frozen" else Money.format(it.balanceCents)}" }, { vm.set("operationCard", it) }, !busy)
            if (!topUp) {
                ChoiceField("Merchant", merchantId, merchants.map { it.id to "${it.name} · ${it.category.label()}" }, { vm.set("operationMerchant", it) }, !busy)
                merchants.find { it.id == merchantId }?.let { Text("${it.district} · ${it.description}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                platform.scanMerchant?.let { scan -> OutlinedButton({ scan { id, cents -> vm.set("operationMerchant", id); cents?.let { vm.set("operationAmount", amountInput(it)) } } }) { Icon(Icons.Outlined.QrCodeScanner, null); Spacer(Modifier.width(8.dp)); Text("Scan demo merchant QR") } }
            }
            OutlinedTextField(amount, { vm.set("operationAmount", it.take(24)); vm.set("operationError", "") }, Modifier.fillMaxWidth(), label = { Text("Amount in HKD") }, prefix = { Text("HK$") }, placeholder = { Text("0.00") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, isError = error.isNotBlank(), enabled = !busy)
            if (topUp) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("50", "100", "200", "500").forEach { preset -> FilterChip(amount == preset, { vm.set("operationAmount", preset); vm.set("operationError", "") }, label = { Text("$$preset") }, modifier = Modifier.weight(1f)) }
            }
            if (error.isNotBlank()) Notice(error, true)
        }
        Notice("You will review everything before confirming. This is a simulation; no bank account or real money is involved.")
        PrimaryButton("Review ${if (topUp) "top-up" else "payment"}", !busy) { vm.review() }
    }
}

@Composable
fun ReviewScreen(vm: WalletViewModel) {
    val cards by vm.cards.collectAsStateWithLifecycle()
    val merchants by vm.merchants.collectAsStateWithLifecycle()
    val kind by vm.text("operationKind").collectAsStateWithLifecycle()
    val cardId by vm.text("operationCard").collectAsStateWithLifecycle()
    val amount by vm.text("operationAmount").collectAsStateWithLifecycle()
    val merchantId by vm.text("operationMerchant").collectAsStateWithLifecycle()
    val error by vm.operationError.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val operationState by vm.operationState.collectAsStateWithLifecycle()
    val unresolved = operationState == "PENDING" || operationState == "UNCERTAIN"
    val card = cards.find { it.id == cardId }
    val title = when (kind) { "TOPUP" -> "top-up"; "PASS" -> "pass purchase"; "SUBSIDY" -> "demo subsidy claim"; else -> "payment" }
    ScreenColumn {
        PageHeading("02 / Review", "Check, then confirm.", "Review your simulated $title.")
        Panel {
            Text(Money.format(Money.parseCents(amount) ?: 0L), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            if (kind == "SUBSIDY") Text("Fictional subsidy credit", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            DetailRow("Card", card?.let { "${it.name} · ${it.maskedNumber}" } ?: "Card unavailable")
            DetailRow("Current balance", card?.let { Money.format(it.balanceCents) } ?: "—")
            DetailRow("Type", title.replaceFirstChar(Char::uppercaseChar))
            when (kind) {
                "TOPUP" -> DetailRow("Source", "TapLink demo funds")
                "PAYMENT" -> DetailRow("Merchant", merchants.find { it.id == merchantId }?.name ?: "Unavailable")
                "PASS" -> { DetailRow("Pass", PassCatalog.MONTHLY_PASS_NAME); DetailRow("Validity", "One calendar month from purchase") }
                "SUBSIDY" -> DetailRow("Claim month", vm.value("operationMonth"))
            }
            if ((kind == "PAYMENT" || kind == "PASS") && prefs.autoReloadEnabled) Notice("Automatic reload is on: ${Money.format(prefs.autoReloadAmountCents)} is added if the balance after payment falls below ${Money.format(prefs.autoReloadThresholdCents)}. Your starting balance must cover the payment.")
            if (card?.isFrozen == true) Notice("This card is frozen. Return Home to unfreeze it.", true)
            if (error.isNotBlank()) Notice(error, true)
        }
        Notice("Simulated transaction only. Confirm once; if a retry is needed, TapLink keeps the same request to prevent duplicates.")
        PrimaryButton(if (unresolved) "Retry this same request" else "Confirm $title", unresolved || (card != null && !card.isFrozen), busy) { vm.confirm() }
        if (unresolved) OutlinedButton({ vm.checkOperationOutcome() }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Check for saved receipt") }
        OutlinedButton({ vm.back() }, Modifier.fillMaxWidth(), enabled = !busy) { Text(if (unresolved) "Return Home · keep request" else "Back to details") }
    }
}

@Composable
fun ReceiptScreen(vm: WalletViewModel, platform: PlatformActions, detail: Boolean = false) {
    val receipt by vm.receipt.collectAsStateWithLifecycle()
    val transactions by vm.transactions.collectAsStateWithLifecycle()
    val cards by vm.cards.collectAsStateWithLifecycle()
    val expectedOperation by vm.text(if (detail) "detailOperation" else "receiptOperation").collectAsStateWithLifecycle()
    val loading by vm.receiptLoading.collectAsStateWithLifecycle()
    val receiptError by vm.receiptError.collectAsStateWithLifecycle()
    val matchingReceipt = receipt.matchingOperation(expectedOperation)
    val tx = if (detail) transactions.find { it.id == vm.value("detailTransaction") } else matchingReceipt?.transaction
    ScreenColumn {
        if (tx == null) {
            if (loading) { CircularProgressIndicator(); Text("Loading your saved receipt…") }
            else EmptyState("Receipt unavailable", receiptError ?: "Your saved activity is available from Home.")
            OutlinedButton({ vm.loadCurrentReceipt() }, Modifier.fillMaxWidth(), enabled = !loading) { Text("Reload receipt") }
            PrimaryButton("Return Home") { vm.root("Home") }
        } else {
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (detail) Icons.Outlined.ReceiptLong else Icons.Outlined.CheckCircle, null, Modifier.size(60.dp), tint = MaterialTheme.colorScheme.secondary)
                Text(if (detail) "Transaction details" else "You're all set.", style = MaterialTheme.typography.headlineMedium)
                Text("${if (tx.isDebit) "−" else "+"}${Money.format(tx.amountCents)}", style = MaterialTheme.typography.headlineLarge)
                Text(tx.type.label(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Panel {
                DetailRow("Status", "Completed · Simulated", true)
                DetailRow("Merchant / source", tx.merchantName)
                DetailRow("Card", cards.find { it.id == tx.cardId }?.name ?: tx.cardId)
                DetailRow("Date & time", "${dateTime(tx.createdAt)} HKT")
                DetailRow("Category", tx.category.label())
                val matching = matchingReceipt
                matching?.autoReload?.let { DetailRow("Automatic reload", "+${Money.format(it.amountCents)}") }
                DetailRow("Resulting balance", Money.format(matching?.finalBalanceCents ?: tx.balanceAfterCents), true)
                matching?.pass?.let { DetailRow("Pass valid until", dateTime(it.validUntil)) }
                HorizontalDivider()
                Text("Receipt ID", style = MaterialTheme.typography.labelSmall)
                Text(tx.id, style = MaterialTheme.typography.bodySmall)
                if (tx.note.isNotBlank()) Text(tx.note, style = MaterialTheme.typography.bodySmall)
            }
            Notice("Educational prototype — no real payment, transport ticket, or official subsidy.")
            platform.shareReceipt?.let { share ->
                val stored = matchingReceipt?.takeIf { it.transaction.id == tx.id }
                if (stored != null) OutlinedButton({ share(stored) }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(8.dp)); Text("Share simulated receipt") }
            }
            PrimaryButton(if (detail) "Back to activity" else "Done") { if (detail) vm.back() else vm.root("Home") }
        }
    }
}
