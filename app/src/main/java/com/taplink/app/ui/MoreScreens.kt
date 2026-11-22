package com.taplink.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taplink.app.domain.*

@Composable
fun MoreScreen(vm: WalletViewModel) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val unread = messages.count { !it.isRead }
    ScreenColumn {
        PageHeading("A wallet that fits you", "Make it yours.", "Your messages, preferences, and useful extras.")
        NavigationRow(Icons.Outlined.Notifications, "Messages", if (unread == 0) "You're all caught up" else "$unread unread ${if (unread == 1) "message" else "messages"}") { vm.navigate("Messages") }
        NavigationRow(Icons.Outlined.Tune, "Settings", "Appearance, reloads, privacy and reminders") { vm.navigate("Settings") }
        NavigationRow(Icons.Outlined.DirectionsTransit, "Transport", "Demo monthly pass and fictional subsidies") { vm.navigate("Transport") }
        NavigationRow(Icons.Outlined.LocalOffer, "Offers & rewards", "Saved offers and simulated coupons") { vm.navigate("Offers") }
        Panel {
            Text("TapLink", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text("Your everyday connection.", style = MaterialTheme.typography.bodyLarge)
            Text("Version 1.0 · Educational Android prototype", style = MaterialTheme.typography.bodySmall)
            Notice("All balances, payments, passes, rewards, and subsidies are simulated. TapLink is not affiliated with Octopus and does not connect to banking or transport payment systems.")
        }
    }
}

@Composable
fun MessagesScreen(vm: WalletViewModel) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val transactions by vm.transactions.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    ScreenColumn {
        PageHeading("Message centre", "Stay in the loop.", "Wallet updates and reminders, saved in the app.")
        if (messages.any { !it.isRead }) TextButton({ vm.act("All messages marked read", { vm.repository.markAllMessagesRead() }) }, enabled = !busy) { Text("Mark all as read") }
        if (messages.isEmpty()) EmptyState("All quiet for now", "New wallet updates will be available here even when system notifications are unavailable.", Icons.Outlined.NotificationsNone)
        messages.forEach { message -> Panel {
            Text(if (message.isRead) message.type.label() else "● NEW · ${message.type.label()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            Text(message.title, style = MaterialTheme.typography.titleMedium, fontWeight = if (message.isRead) FontWeight.Medium else FontWeight.Bold)
            Text(message.body, style = MaterialTheme.typography.bodyMedium)
            Text("${dateTime(message.createdAt)} HKT", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!message.isRead) TextButton({ vm.act(action = { vm.repository.markMessageRead(message.id) }) }, enabled = !busy) { Text("Mark read") }
                transactions.find { it.id == message.transactionId }?.let { tx -> TextButton({ vm.openTransaction(tx) }, enabled = !busy) { Text("View transaction") } }
            }
        } }
    }
}

@Composable
fun SettingsScreen(vm: WalletViewModel, platform: PlatformActions) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val cards by vm.cards.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val threshold by vm.text("settingsThreshold", amountInput(prefs.lowBalanceThresholdCents)).collectAsStateWithLifecycle()
    val reloadThreshold by vm.text("settingsReloadThreshold", amountInput(prefs.autoReloadThresholdCents)).collectAsStateWithLifecycle()
    val reloadAmount by vm.text("settingsReloadAmount", amountInput(prefs.autoReloadAmountCents)).collectAsStateWithLifecycle()
    val settingsError by vm.text("settingsError").collectAsStateWithLifecycle()
    ScreenColumn {
        PageHeading("Settings", "Your wallet, your way.", "Preferences are saved on this device.")
        Panel {
            SectionHeading("Appearance & cards")
            ChoiceField("Theme", prefs.theme, ThemeMode.entries.map { it to it.label() }, { mode -> vm.updatePreferences { it.copy(theme = mode) } }, !busy)
            ChoiceField("Default / selected card", prefs.selectedCardId.orEmpty(), cards.map { it.id to it.name }, { id -> vm.act(action = { vm.repository.selectCard(id) }) }, !busy)
        }
        Panel {
            SectionHeading("Balance & privacy")
            PreferenceSwitch("Show balance", "Reveal the balance on your wallet card.", prefs.balanceVisible, !busy) { checked -> vm.updatePreferences { it.copy(balanceVisible = checked) } }
            PreferenceSwitch("Show balance on widget", "Allow the home-screen widget to display your balance outside the app.", prefs.widgetBalanceVisible, !busy) { checked -> vm.updatePreferences { it.copy(widgetBalanceVisible = checked) } }
            PreferenceSwitch("Shake to hide or reveal", "Optional gesture, active only while Home is visible. The eye button is always available.", prefs.shakeToToggleEnabled, !busy) { checked -> vm.updatePreferences { it.copy(shakeToToggleEnabled = checked) } }
            platform.pinWidget?.let { pin -> OutlinedButton(pin, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Widgets, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Add home-screen widget") } }
        }
        Panel {
            SectionHeading("Alerts & reminders")
            PreferenceSwitch("Low-balance alerts", "Based on the final committed balance after a payment.", prefs.lowBalanceAlerts, !busy) { checked -> vm.updatePreferences { it.copy(lowBalanceAlerts = checked) } }
            PreferenceSwitch("Scheduled reminders", "Deferrable updates; delivery time may vary.", prefs.remindersEnabled, !busy) { checked -> vm.updatePreferences { it.copy(remindersEnabled = checked) } }
            PreferenceSwitch("Expiry reminders", "Reminders for demo passes and rewards.", prefs.expiryRemindersEnabled, !busy) { checked -> vm.updatePreferences { it.copy(expiryRemindersEnabled = checked) } }
            PreferenceSwitch("Refresh demo content", "Keep local sample content up to date in the background.", prefs.demoRefreshEnabled, !busy) { checked -> vm.updatePreferences { it.copy(demoRefreshEnabled = checked) } }
            platform.requestNotifications?.let { request -> OutlinedButton(request, Modifier.fillMaxWidth()) { Text("Manage system notifications") } }
            Text("In-app messages remain available when notifications are disabled or permission is denied.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Panel {
            SectionHeading("Automatic reload & thresholds")
            PreferenceSwitch("Automatic demo reload", "Add simulated funds once after an affordable payment leaves a low balance.", prefs.autoReloadEnabled, !busy) { checked -> vm.updatePreferences { it.copy(autoReloadEnabled = checked) } }
            MoneySetting("Low-balance alert threshold", threshold, !busy) { vm.set("settingsThreshold", it) }
            MoneySetting("Reload when balance is below", reloadThreshold, !busy) { vm.set("settingsReloadThreshold", it) }
            MoneySetting("Automatic reload amount", reloadAmount, !busy) { vm.set("settingsReloadAmount", it) }
            if (settingsError.isNotBlank()) Notice(settingsError, true)
            PrimaryButton("Save amounts", !busy, busy) {
                val alert = parseThreshold(threshold)
                val reload = parseThreshold(reloadThreshold)
                val amount = Money.parseCents(reloadAmount)
                if (alert == null || reload == null || amount == null) vm.set("settingsError", "Enter valid HKD amounts with up to two decimal places. The reload amount must be greater than zero.")
                else {
                    vm.set("settingsError", "")
                    vm.updatePreferences("Amounts saved") { it.copy(lowBalanceThresholdCents = alert, autoReloadThresholdCents = reload, autoReloadAmountCents = amount) }
                }
            }
            Text("Automatic reload cannot rescue an initially unaffordable payment. All funds are simulated.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Panel {
            SectionHeading("Demo content")
            Text("Refresh sample content without resetting your cards, transactions, saved offers, or preferences.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton({ vm.act("Demo content refreshed", { vm.repository.refreshDemoContent() }) }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Refresh now") }
        }
    }
}

private fun parseThreshold(value: String): Long? = if (value.trim().matches(Regex("0+(\\.0{1,2})?"))) 0L else Money.parseCents(value)

@Composable
private fun MoneySetting(label: String, value: String, enabled: Boolean, change: (String) -> Unit) {
    OutlinedTextField(value, { change(it.take(24)) }, Modifier.fillMaxWidth(), label = { Text(label) }, prefix = { Text("HK$") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = enabled)
}
