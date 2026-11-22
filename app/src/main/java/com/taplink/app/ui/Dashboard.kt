package com.taplink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    ScreenColumn {
        PageHeading("Your everyday wallet", "A little tap. A lot closer.", "Everything you need for your next stop.")
        if (card != null) {
            Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF1457B5), Color(0xFF087F91))), RoundedCornerShape(26.dp)).padding(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Contactless, null, tint = Color.White, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("TapLink", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.weight(1f))
                        Surface(color = Color.White.copy(alpha = 0.16f), shape = RoundedCornerShape(50)) { Text(if (card.isFrozen) "FROZEN" else "DEMO CARD", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = Color.White) }
                    }
                    Column {
                        Text("Available balance · HKD", color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (prefs.balanceVisible) Money.format(card.balanceCents) else "HK$ ••••", Modifier.weight(1f), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                            IconButton(onClick = { vm.updatePreferences { it.copy(balanceVisible = !it.balanceVisible) } }, enabled = !busy) { Icon(if (prefs.balanceVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, if (prefs.balanceVisible) "Hide balance" else "Show balance", tint = Color.White) }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(card.name, Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Text(card.maskedNumber, color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            ChoiceField("Current card", card.id, cards.map { it.id to "${it.name}${if (it.isFrozen) " · Frozen" else ""}" }, { vm.act(action = { vm.repository.selectCard(it) }) }, !busy)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button({ vm.startOperation("TOPUP") }, Modifier.weight(1f).heightIn(min = 54.dp), enabled = !busy, shape = RoundedCornerShape(16.dp)) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("Top up") }
                FilledTonalButton({ vm.startOperation("PAYMENT") }, Modifier.weight(1f).heightIn(min = 54.dp), enabled = !busy, shape = RoundedCornerShape(16.dp)) { Icon(Icons.Outlined.NorthEast, null); Spacer(Modifier.width(6.dp)); Text("Pay") }
            }
            if (card.isFrozen) Notice("This card is frozen. Unfreeze it to top up, pay, or buy a demo pass.")
            else if (card.balanceCents < prefs.lowBalanceThresholdCents) Notice("Your balance is running low. A demo top-up can keep your day moving.")
            Panel {
                SectionHeading("Your card, your control")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton({ vm.openCardDialog("create") }, enabled = !busy) { Icon(Icons.Outlined.AddCard, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("New") }
                    TextButton({ vm.openCardDialog("rename", card) }, enabled = !busy) { Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Rename") }
                    TextButton({ vm.openCardDialog(if (card.isFrozen) "unfreeze" else "freeze", card) }, enabled = !busy) { Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (card.isFrozen) "Unfreeze" else "Freeze") }
                }
            }
        } else {
            EmptyState("Your first card starts here", "Create a demo card and add simulated funds.", Icons.Outlined.CreditCard)
            PrimaryButton("Create a card", !busy) { vm.openCardDialog("create") }
        }
        Panel {
            SectionHeading("Recent activity", "View all") { vm.root("Activity") }
            val recent = transactions.filter { it.cardId == card?.id }.take(4)
            if (recent.isEmpty()) EmptyState("Ready for your first tap", "Top-ups and payments will appear here.")
            recent.forEach { TransactionRow(it) { vm.openTransaction(it) } }
        }
        Text("Educational prototype · Simulated balances and payments only", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (dialog.isNotBlank()) {
        val editing = dialog == "create" || dialog == "rename"
        AlertDialog(
            onDismissRequest = vm::dismissCardDialog,
            icon = { Icon(if (editing) Icons.Outlined.CreditCard else Icons.Outlined.Lock, null) },
            title = { Text(when (dialog) { "create" -> "Create a demo card"; "rename" -> "Rename card"; "freeze" -> "Freeze this card?"; else -> "Unfreeze this card?" }) },
            text = {
                if (editing) OutlinedTextField(name, vm::editCardDialogName, label = { Text("Card name") }, singleLine = true, supportingText = { Text("Choose a name, such as Campus or Weekend.") }, enabled = !busy)
                else Text(if (dialog == "freeze") "Top-ups, payments and pass purchases will be blocked until you unfreeze it. Your balance stays safe." else "This card will be able to make simulated transactions again.")
            },
            confirmButton = { TextButton(enabled = !busy && (!editing || name.isNotBlank()), onClick = vm::submitCardDialog) { Text(if (busy) "Saving…" else "Confirm") } },
            dismissButton = { TextButton(vm::dismissCardDialog, enabled = !busy) { Text("Cancel") } }
        )
    }
}
