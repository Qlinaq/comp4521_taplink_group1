package com.taplink.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taplink.app.R
import com.taplink.app.domain.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OperationForm(vm: WalletViewModel, platform: PlatformActions) {    val kind by vm.text("operationKind").collectAsStateWithLifecycle()
    val cardId by vm.text("operationCard").collectAsStateWithLifecycle()
    val merchantId by vm.text("operationMerchant").collectAsStateWithLifecycle()
    val amount by vm.text("operationAmount").collectAsStateWithLifecycle()
    val errorRaw by vm.operationError.collectAsStateWithLifecycle()
    val error = localizedMessage(errorRaw).orEmpty()
    val cards by vm.cards.collectAsStateWithLifecycle()
    val merchants by vm.merchants.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val topUp = kind == "TOPUP"
    ScreenColumn {
        PageHeading( stringResource(R.string.ui_01_enter_details) , if (topUp) stringResource(R.string.ui_a_boost_for_your_day) else stringResource(R.string.ui_make_your_next_tap), if (topUp) stringResource(R.string.ui_add_simulated_hkd_from_the_taplink_demo_sour) else stringResource(R.string.ui_choose_a_demo_merchant_and_enter_your_amount))
        Panel {
            ChoiceField( stringResource(R.string.ui_pay_with_top_up) , cardId, cards.map { it.id to "${it.name} · ${if (it.isFrozen) stringResource(R.string.ui_frozen) else Money.format(it.balanceCents)}" }, { vm.set("operationCard", it) }, !busy)
            if (!topUp) {
                ChoiceField( stringResource(R.string.ui_merchant) , merchantId, merchants.map { it.id to "${it.name} · ${it.category.label()}" }, { vm.set("operationMerchant", it) }, !busy)
                merchants.find { it.id == merchantId }?.let { Text("${it.district} · ${it.description}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                platform.scanMerchant?.let { scan -> OutlinedButton({ scan { id, cents -> vm.set("operationMerchant", id); cents?.let { vm.set("operationAmount", amountInput(it)) } } }) { Icon(Icons.Outlined.QrCodeScanner, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ui_scan_demo_merchant_qr)) } }
            }
            OutlinedTextField(amount, { vm.set("operationAmount", it.take(24)); vm.set("operationError", "") }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.ui_amount_in_hkd)) }, prefix = { Text("HK$") }, placeholder = { Text(stringResource(R.string.ui_0_00)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, isError = error.isNotBlank(), enabled = !busy)
            // Presets wrap instead of sharing the width equally: at 150% text an equal-weight row
            // broke "$100" into "$1 / 00".
            if (topUp) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("50", "100", "200", "500").forEach { preset ->
                    FilterChip(amount == preset, { vm.set("operationAmount", preset); vm.set("operationError", "") }, label = { Text("$$preset", maxLines = 1) })
                }
            }
            if (error.isNotBlank()) Notice(error, true)
        }
        Notice( stringResource(R.string.ui_you_will_review_everything_before_confirming) )
        PrimaryButton(stringResource(R.string.ui_review_kind, stringResource(if (topUp) R.string.ui_kind_top_up else R.string.ui_kind_payment)), !busy) { vm.review() }
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
    val errorRaw by vm.operationError.collectAsStateWithLifecycle()
    val error = localizedMessage(errorRaw).orEmpty()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val operationState by vm.operationState.collectAsStateWithLifecycle()
    val unresolved = operationState == "PENDING" || operationState == "UNCERTAIN"
    val card = cards.find { it.id == cardId }
    val title = when (kind) { "TOPUP" -> R.string.ui_kind_top_up; "PASS" -> R.string.ui_kind_pass; "SUBSIDY" -> R.string.ui_kind_subsidy; else -> R.string.ui_kind_payment }
    val titleText = stringResource(title)
    ScreenColumn {
        PageHeading( stringResource(R.string.ui_02_review) , stringResource(R.string.ui_check_then_confirm) , stringResource(R.string.ui_review_simulated_kind, titleText))
        Panel {
            Text(Money.format(Money.parseCents(amount) ?: 0L), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            if (kind == "SUBSIDY") Text(stringResource(R.string.ui_fictional_subsidy_credit), style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            DetailRow( stringResource(R.string.ui_card) , card?.let { "${it.name} · ${it.maskedNumber}" } ?: stringResource(R.string.ui_card_unavailable))
            DetailRow( stringResource(R.string.ui_current_balance) , card?.let { Money.format(it.balanceCents) } ?: "—")
            DetailRow( stringResource(R.string.ui_type) , titleText.replaceFirstChar(Char::uppercaseChar))
            when (kind) {
                "TOPUP" -> DetailRow( stringResource(R.string.ui_source) , stringResource(R.string.ui_taplink_demo_funds) )
                "PAYMENT" -> DetailRow( stringResource(R.string.ui_merchant) , merchants.find { it.id == merchantId }?.name ?: stringResource(R.string.ui_unavailable))
                "PASS" -> { DetailRow( stringResource(R.string.ui_pass) , PassCatalog.MONTHLY_PASS_NAME); DetailRow( stringResource(R.string.ui_validity) , stringResource(R.string.ui_one_calendar_month_from_purchase) ) }
                "SUBSIDY" -> DetailRow( stringResource(R.string.ui_claim_month) , monthLabel(vm.value("operationMonth")))
            }
            if ((kind == "PAYMENT" || kind == "PASS") && prefs.autoReloadEnabled) Notice(stringResource(R.string.ui_auto_reload_notice, Money.format(prefs.autoReloadAmountCents), Money.format(prefs.autoReloadThresholdCents)))
            if (card?.isFrozen == true) Notice( stringResource(R.string.ui_this_card_is_frozen_return_home_to_unfreeze) , true)
            if (error.isNotBlank()) Notice(error, true)
        }
        Notice( stringResource(R.string.ui_simulated_transaction_only_confirm_once_if_a) )
        PrimaryButton(if (unresolved) stringResource(R.string.ui_retry_same_request) else stringResource(R.string.ui_confirm_kind, titleText), unresolved || (card != null && !card.isFrozen), busy) { vm.confirm() }
        if (unresolved) OutlinedButton({ vm.checkOperationOutcome() }, Modifier.fillMaxWidth(), enabled = !busy) { Text(stringResource(R.string.ui_check_for_saved_receipt)) }
        OutlinedButton({ vm.back() }, Modifier.fillMaxWidth(), enabled = !busy) { Text(if (unresolved) stringResource(R.string.ui_return_home_keep_request) else stringResource(R.string.ui_back_to_details)) }
    }
}

@Composable
fun ReceiptScreen(vm: WalletViewModel, platform: PlatformActions, detail: Boolean = false) {
    val receipt by vm.receipt.collectAsStateWithLifecycle()
    val transactions by vm.transactions.collectAsStateWithLifecycle()
    val cards by vm.cards.collectAsStateWithLifecycle()
    val expectedOperation by vm.text(if (detail) "detailOperation" else "receiptOperation").collectAsStateWithLifecycle()
    val loading by vm.receiptLoading.collectAsStateWithLifecycle()
    val receiptErrorRaw by vm.receiptError.collectAsStateWithLifecycle()
    val receiptError = localizedMessage(receiptErrorRaw)
    val matchingReceipt = receipt.matchingOperation(expectedOperation)
    val tx = if (detail) transactions.find { it.id == vm.value("detailTransaction") } else matchingReceipt?.transaction
    ScreenColumn {
        if (tx == null) {
            if (loading) { CircularProgressIndicator(); Text(stringResource(R.string.ui_loading_your_saved_receipt)) }
            else EmptyState( stringResource(R.string.ui_receipt_unavailable) , receiptError ?: stringResource(R.string.receipt_history_available))
            OutlinedButton({ vm.loadCurrentReceipt() }, Modifier.fillMaxWidth(), enabled = !loading) { Text(stringResource(R.string.ui_reload_receipt)) }
            PrimaryButton( stringResource(R.string.ui_return_home) ) { vm.root("Home") }
        } else {
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (detail) Icons.AutoMirrored.Outlined.ReceiptLong else Icons.Outlined.CheckCircle, null, Modifier.size(60.dp), tint = MaterialTheme.colorScheme.secondary)
                Text(stringResource(if (detail) R.string.receipt_details_title else R.string.receipt_done_title), style = MaterialTheme.typography.headlineMedium)
                Text("${if (tx.isDebit) "−" else "+"}${Money.format(tx.amountCents)}", style = MaterialTheme.typography.headlineLarge)
                Text(tx.type.label(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Panel {
                DetailRow( stringResource(R.string.ui_status) , stringResource(R.string.ui_completed_simulated) , true)
                DetailRow( stringResource(R.string.ui_merchant_source) , tx.merchantName)
                DetailRow( stringResource(R.string.ui_card) , cards.find { it.id == tx.cardId }?.name ?: tx.cardId)
                DetailRow( stringResource(R.string.ui_date_time) , "${dateTime(tx.createdAt)} HKT")
                DetailRow( stringResource(R.string.ui_category) , tx.category.label())
                val matching = matchingReceipt
                matching?.autoReload?.let { DetailRow( stringResource(R.string.ui_automatic_reload) , "+${Money.format(it.amountCents)}") }
                DetailRow( stringResource(R.string.ui_resulting_balance) , Money.format(matching?.finalBalanceCents ?: tx.balanceAfterCents), true)
                matching?.pass?.let { DetailRow( stringResource(R.string.ui_pass_valid_until) , dateTime(it.validUntil)) }
                HorizontalDivider()
                Text(stringResource(R.string.ui_receipt_id), style = MaterialTheme.typography.labelSmall)
                Text(tx.id, style = MaterialTheme.typography.bodySmall)
                if (tx.note.isNotBlank()) Text(tx.note, style = MaterialTheme.typography.bodySmall)
            }
            Notice( stringResource(R.string.ui_educational_prototype_no_real_payment_transp) )
            platform.shareReceipt?.let { share ->
                val stored = matchingReceipt?.takeIf { it.transaction.id == tx.id }
                if (stored != null) OutlinedButton({ share(stored) }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ui_share_simulated_receipt)) }
            }
            PrimaryButton(stringResource(if (detail) R.string.ui_back_to_activity else R.string.ui_done)) { if (detail) vm.back() else vm.root("Home") }
        }
    }
}
