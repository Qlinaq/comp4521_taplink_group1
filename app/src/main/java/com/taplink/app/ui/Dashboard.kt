package com.taplink.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taplink.app.R
import com.taplink.app.domain.*

@Composable
fun Dashboard(vm: WalletViewModel) {
    val cards by vm.cards.collectAsStateWithLifecycle()
    val selected by vm.selectedCard.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val transactions by vm.transactions.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val dialog by vm.cardDialog.collectAsStateWithLifecycle()
    val name by vm.cardDialogName.collectAsStateWithLifecycle()
    val card = selected
    val simple = prefs.elderlyMode
    ScreenColumn {
        // Hero form: the greeting sits on a tinted card with a pill eyebrow, so the first thing on
        // the screen is a surface instead of bare text. The standard greeting is two lines only —
        // the tagline under it was removed because the first card already says what the app is for;
        // the simplified (elderly) greeting keeps its explanatory line.
        PageHeading(
            eyebrow = stringResource(if (simple) R.string.home_simple_eyebrow else R.string.ui_your_everyday_wallet),
            title = stringResource(if (simple) R.string.home_simple_title else R.string.ui_a_little_tap_a_lot_closer),
            subtitle = if (simple) stringResource(R.string.home_simple_subtitle) else null,
            hero = true
        )
        if (simple) ModeBanner(stringResource(R.string.a11y_mode_active))
        if (card != null) {
            VoiceSummary(
                buildString {
                    append(stringResource(R.string.home_voice_card, card.name))
                    if (prefs.balanceVisible) append(stringResource(R.string.home_voice_balance, Money.format(card.balanceCents)))
                    if (card.isFrozen) append(stringResource(R.string.home_voice_frozen))
                    else if (card.balanceCents < prefs.lowBalanceThresholdCents) append(stringResource(R.string.home_voice_low))
                }
            )
            CardPager(
                cards = cards,
                selected = card,
                prefs = prefs,
                busy = busy,
                onSelect = { page -> vm.act(action = { vm.repository.selectCard(page.id) }) },
                onToggleBalance = { vm.updatePreferences { it.copy(balanceVisible = !it.balanceVisible) } }
            )
            if (!simple) ChoiceField( stringResource(R.string.ui_current_card) , card.id, cards.map { it.id to "${it.name}${if (it.isFrozen) " · Frozen" else ""}" }, { vm.act(action = { vm.repository.selectCard(it) }) }, !busy)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val buttonHeight = if (simple) 76.dp else 54.dp
                Button({ vm.startOperation("TOPUP") }, Modifier.weight(1f).heightIn(min = buttonHeight), enabled = !busy, shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(if (simple) 26.dp else 20.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.ui_top_up), style = if (simple) MaterialTheme.typography.titleMedium else LocalTextStyle.current)
                }
                FilledTonalButton({ vm.startOperation("PAYMENT") }, Modifier.weight(1f).heightIn(min = buttonHeight), enabled = !busy, shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Outlined.NorthEast, null, Modifier.size(if (simple) 26.dp else 20.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.ui_pay), style = if (simple) MaterialTheme.typography.titleMedium else LocalTextStyle.current)
                }
            }
            if (card.isFrozen) Notice( stringResource(R.string.ui_this_card_is_frozen_unfreeze_it_to_top_up_pa) )
            else if (card.balanceCents < prefs.lowBalanceThresholdCents) Notice( stringResource(R.string.ui_your_balance_is_running_low_a_demo_top_up_ca) )
            // With the simplified flow on, card management stays reachable in Settings but is not
            // pushed at a user who only wants to pay.
            if (!simple) Panel {
                SectionHeading( stringResource(R.string.ui_your_card_your_control) )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton({ vm.openCardDialog("create") }, enabled = !busy) { Icon(Icons.Outlined.AddCard, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.ui_new)) }
                    TextButton({ vm.openCardDialog("rename", card) }, enabled = !busy) { Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.ui_rename)) }
                    TextButton({ vm.openCardDialog(if (card.isFrozen) "unfreeze" else "freeze", card) }, enabled = !busy) { Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (card.isFrozen) stringResource(R.string.ui_unfreeze) else stringResource(R.string.ui_freeze)) }
                }
            }
        } else {
            EmptyState( stringResource(R.string.ui_your_first_card_starts_here) , stringResource(R.string.ui_create_a_demo_card_and_add_simulated_funds) , Icons.Outlined.CreditCard)
            PrimaryButton( stringResource(R.string.ui_create_a_card) , !busy) { vm.openCardDialog("create") }
        }
        Panel {
            SectionHeading( stringResource(R.string.ui_recent_activity) , stringResource(R.string.ui_view_all) ) { vm.root("Activity") }
            val recent = transactions.filter { it.cardId == card?.id }.take(if (simple) 3 else 4)
            if (recent.isEmpty()) EmptyState( stringResource(R.string.ui_ready_for_your_first_tap) , stringResource(R.string.ui_top_ups_and_payments_will_appear_here) )
            recent.forEach { TransactionRow(it) { vm.openTransaction(it) } }
        }
        if (simple) NavigationRow(Icons.Outlined.SupportAgent, stringResource(R.string.a11y_contact_support), stringResource(R.string.a11y_contact_support_body)) { vm.navigate("Contact") }
        Text(stringResource(R.string.ui_educational_prototype_simulated_balances_and), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (dialog.isNotBlank()) {
        val editing = dialog == "create" || dialog == "rename"
        AlertDialog(
            onDismissRequest = vm::dismissCardDialog,
            icon = { Icon(if (editing) Icons.Outlined.CreditCard else Icons.Outlined.Lock, null) },
            title = { Text(when (dialog) { "create" -> stringResource(R.string.ui_create_a_demo_card); "rename" -> stringResource(R.string.ui_rename_card); "freeze" -> stringResource(R.string.ui_freeze_this_card); else -> stringResource(R.string.ui_unfreeze_this_card) }) },
            text = {
                if (editing) OutlinedTextField(name, vm::editCardDialogName, label = { Text(stringResource(R.string.ui_card_name)) }, singleLine = true, supportingText = { Text(stringResource(R.string.ui_choose_a_name_such_as_campus_or_weekend)) }, enabled = !busy)
                else Text(if (dialog == "freeze") stringResource(R.string.ui_top_ups_payments_and_pass_purchases_will_be) else stringResource(R.string.ui_this_card_will_be_able_to_make_simulated_tra))
            },
            confirmButton = { TextButton(enabled = !busy && (!editing || name.isNotBlank()), onClick = vm::submitCardDialog) { Text(if (busy) stringResource(R.string.ui_saving) else stringResource(R.string.ui_confirm)) } },
            dismissButton = { TextButton(vm::dismissCardDialog, enabled = !busy) { Text(stringResource(R.string.account_cancel)) } }
        )
    }
}
