package com.taplink.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taplink.app.R
import com.taplink.app.domain.*
import com.taplink.app.platform.MessageCopy
import com.taplink.app.ui.auth.AccountSection

@Composable
fun MoreScreen(vm: WalletViewModel) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val unread = messages.count { !it.isRead }
    ScreenColumn {
        // Tab roots (Home, Activity, Explore, More) all open on the same hero heading, so the top of
        // every tab reads as one system rather than three different treatments.
        PageHeading( stringResource(R.string.ui_a_wallet_that_fits_you) , stringResource(R.string.ui_make_it_yours) , stringResource(R.string.ui_your_messages_preferences_and_useful_extras) , hero = true )
        NavigationRow(Icons.Outlined.Notifications, stringResource(R.string.ui_messages) , if (unread == 0) stringResource(R.string.ui_you_re_all_caught_up) else stringResource(if (unread == 1) R.string.ui_unread_one else R.string.ui_unread_many, unread)) { vm.navigate("Messages") }
        NavigationRow(Icons.Outlined.Tune, stringResource(R.string.ui_settings) , stringResource(R.string.ui_appearance_reloads_privacy_and_reminders) ) { vm.navigate("Settings") }
        NavigationRow(Icons.Outlined.NotificationsActive, stringResource(R.string.more_notifications), stringResource(R.string.more_notifications_body)) { vm.navigate("Notifications") }
        NavigationRow(Icons.Outlined.Accessibility, stringResource(R.string.label_accessibility), stringResource(R.string.more_accessibility_body)) { vm.navigate("Accessibility") }
        NavigationRow(Icons.Outlined.SupportAgent, stringResource(R.string.label_contact_us), stringResource(R.string.more_contact_body)) { vm.navigate("Contact") }
        NavigationRow(Icons.Outlined.DirectionsTransit, stringResource(R.string.ui_transport) , stringResource(R.string.ui_demo_monthly_pass_and_fictional_subsidies) ) { vm.navigate("Transport") }
        NavigationRow(Icons.Outlined.LocalOffer, stringResource(R.string.ui_offers_rewards) , stringResource(R.string.ui_saved_offers_and_simulated_coupons) ) { vm.navigate("Offers") }
        Panel {
            Text("TapLink", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.ui_your_everyday_connection), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.ui_version_1_0_educational_android_prototype), style = MaterialTheme.typography.bodySmall)
            Notice( stringResource(R.string.ui_all_balances_payments_passes_rewards_and_sub) )
        }
    }
}

@Composable
fun MessagesScreen(vm: WalletViewModel) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val transactions by vm.transactions.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ScreenColumn {
        PageHeading( stringResource(R.string.ui_message_centre) , stringResource(R.string.ui_stay_in_the_loop) , stringResource(R.string.ui_wallet_updates_and_reminders_saved_in_the_ap) )
        VoiceSummary(stringResource(R.string.messages_voice_summary, messages.count { !it.isRead }))
        val markAllRead = stringResource(R.string.ui_all_messages_marked_read)
        if (messages.any { !it.isRead }) TextButton({ vm.markAllMessagesRead(markAllRead) }, enabled = !busy) { Text(stringResource(R.string.ui_mark_all_as_read)) }
        if (messages.isEmpty()) EmptyState( stringResource(R.string.ui_all_quiet_for_now) , stringResource(R.string.ui_new_wallet_updates_will_be_available_here_ev) , Icons.Outlined.NotificationsNone)
        messages.forEach { message -> Panel {
            Text(if (message.isRead) message.type.label() else stringResource(R.string.ui_new_chip, message.type.label()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            // Stored English text is only used for rows written before the structured migration.
            Text(MessageCopy.title(context, message), style = MaterialTheme.typography.titleMedium, fontWeight = if (message.isRead) FontWeight.Medium else FontWeight.Bold)
            Text(MessageCopy.body(context, message), style = MaterialTheme.typography.bodyMedium)
            Text("${dateTime(message.createdAt)} HKT", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!message.isRead) TextButton({ vm.markMessageRead(message.id) }, enabled = !busy) { Text(stringResource(R.string.ui_mark_read)) }
                transactions.find { it.id == message.transactionId }?.let { tx -> TextButton({ vm.openTransaction(tx) }, enabled = !busy) { Text(stringResource(R.string.ui_view_transaction)) } }
            }
        } }
    }
}

@Composable
fun AccountScreen() {
    ScreenColumn {
        PageHeading(
            stringResource(R.string.account_heading),
            stringResource(R.string.account_page_subtitle),
            stringResource(R.string.account_page_body)
        )
        AccountSection(showHeading = false)
        Panel {
            SectionHeading(stringResource(R.string.account_scope_title))
            Text(stringResource(R.string.account_scope_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun SettingsScreen(vm: WalletViewModel, platform: PlatformActions) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val cards by vm.cards.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val invalidAmounts = stringResource(R.string.ui_enter_valid_hkd_amounts_with_up_to_two_decim)
    val amountsSaved = stringResource(R.string.ui_amounts_saved)
    val refreshed = stringResource(R.string.ui_demo_content_refreshed)
    val threshold by vm.text("settingsThreshold", amountInput(prefs.lowBalanceThresholdCents)).collectAsStateWithLifecycle()
    val reloadThreshold by vm.text("settingsReloadThreshold", amountInput(prefs.autoReloadThresholdCents)).collectAsStateWithLifecycle()
    val reloadAmount by vm.text("settingsReloadAmount", amountInput(prefs.autoReloadAmountCents)).collectAsStateWithLifecycle()
    val settingsError by vm.text("settingsError").collectAsStateWithLifecycle()
    ScreenColumn {
        PageHeading( stringResource(R.string.ui_settings) , stringResource(R.string.ui_your_wallet_your_way) , stringResource(R.string.ui_preferences_are_saved_on_this_device) )
        AccountSection()
        Panel {
            SectionHeading( stringResource(R.string.ui_appearance_cards) )
            ChoiceField( stringResource(R.string.ui_theme) , prefs.theme, ThemeMode.entries.map { it to it.label() }, { mode -> vm.updatePreferences { it.copy(theme = mode) } }, !busy)
            ChoiceField(
                stringResource(R.string.settings_language),
                prefs.language,
                AppLanguage.entries.map { it to it.nativeName },
                { language -> vm.updatePreferences { it.copy(language = language) } },
                !busy
            )
            Text(
                stringResource(R.string.settings_language_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ChoiceField( stringResource(R.string.ui_default_selected_card) , prefs.selectedCardId.orEmpty(), cards.map { it.id to it.name }, { id -> vm.act(action = { vm.repository.selectCard(id) }) }, !busy)
        }
        Panel {
            SectionHeading( stringResource(R.string.ui_balance_privacy) )
            PreferenceSwitch( stringResource(R.string.ui_show_balance) , stringResource(R.string.ui_reveal_the_balance_on_your_wallet_card) , prefs.balanceVisible, !busy) { checked -> vm.updatePreferences { it.copy(balanceVisible = checked) } }
            PreferenceSwitch( stringResource(R.string.ui_show_balance_on_widget) , stringResource(R.string.ui_allow_the_home_screen_widget_to_display_your) , prefs.widgetBalanceVisible, !busy) { checked -> vm.updatePreferences { it.copy(widgetBalanceVisible = checked) } }
            PreferenceSwitch( stringResource(R.string.ui_shake_to_hide_or_reveal) , stringResource(R.string.ui_optional_gesture_active_only_while_home_is_v) , prefs.shakeToToggleEnabled, !busy) { checked -> vm.updatePreferences { it.copy(shakeToToggleEnabled = checked) } }
            platform.pinWidget?.let { pin -> OutlinedButton(pin, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Widgets, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ui_add_home_screen_widget)) } }
        }
        Panel {
            SectionHeading( stringResource(R.string.ui_alerts_reminders) )
            Text(stringResource(R.string.settings_notifications_shortcut), style = MaterialTheme.typography.bodyMedium)
            // The switches themselves live on one page so a category cannot be toggled in two places
            // and disagree with the notification policy.
            platform.requestNotifications?.let { request -> OutlinedButton(request, Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_manage_system_notifications)) } }
            OutlinedButton({ vm.navigate("Notifications") }, Modifier.fillMaxWidth(), enabled = !busy) {
                Icon(Icons.Outlined.NotificationsActive, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_open_notifications))
            }
            Text(stringResource(R.string.ui_in_app_messages_remain_available_when_notifi), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Panel {
            SectionHeading( stringResource(R.string.ui_automatic_reload_thresholds) )
            PreferenceSwitch( stringResource(R.string.ui_automatic_demo_reload) , stringResource(R.string.ui_add_simulated_funds_once_after_an_affordable) , prefs.autoReloadEnabled, !busy) { checked -> vm.updatePreferences { it.copy(autoReloadEnabled = checked) } }
            MoneySetting( stringResource(R.string.ui_low_balance_alert_threshold) , threshold, !busy) { vm.set("settingsThreshold", it) }
            MoneySetting( stringResource(R.string.ui_reload_when_balance_is_below) , reloadThreshold, !busy) { vm.set("settingsReloadThreshold", it) }
            MoneySetting( stringResource(R.string.ui_automatic_reload_amount) , reloadAmount, !busy) { vm.set("settingsReloadAmount", it) }
            if (settingsError.isNotBlank()) Notice(settingsError, true)
            PrimaryButton( stringResource(R.string.ui_save_amounts) , !busy, busy) {
                val alert = parseThreshold(threshold)
                val reload = parseThreshold(reloadThreshold)
                val amount = Money.parseCents(reloadAmount)
                if (alert == null || reload == null || amount == null) vm.set("settingsError", invalidAmounts)
                else {
                    vm.set("settingsError", "")
                    vm.updatePreferences(amountsSaved) { it.copy(lowBalanceThresholdCents = alert, autoReloadThresholdCents = reload, autoReloadAmountCents = amount) }
                }
            }
            Text(stringResource(R.string.ui_automatic_reload_cannot_rescue_an_initially), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Panel {
            SectionHeading( stringResource(R.string.ui_demo_content) )
            Text(stringResource(R.string.ui_refresh_sample_content_without_resetting_you), style = MaterialTheme.typography.bodyMedium)
            PreferenceSwitch( stringResource(R.string.ui_refresh_demo_content) , stringResource(R.string.ui_keep_local_sample_content_up_to_date_in_the) , prefs.demoRefreshEnabled, !busy) { checked -> vm.updatePreferences { it.copy(demoRefreshEnabled = checked) } }
            OutlinedButton({ vm.act(refreshed, { vm.repository.refreshDemoContent() }) }, Modifier.fillMaxWidth(), enabled = !busy) { Text(stringResource(R.string.ui_refresh_now)) }
        }
        Panel {
            SectionHeading(stringResource(R.string.settings_support_section))
            NavigationRow(Icons.Outlined.Accessibility, stringResource(R.string.label_accessibility), stringResource(R.string.more_accessibility_body)) { vm.navigate("Accessibility") }
            NavigationRow(Icons.Outlined.SupportAgent, stringResource(R.string.label_contact_us), stringResource(R.string.more_contact_body)) { vm.navigate("Contact") }
        }
    }
}

private fun parseThreshold(value: String): Long? = if (value.trim().matches(Regex("0+(\\.0{1,2})?"))) 0L else Money.parseCents(value)

@Composable
private fun MoneySetting(label: String, value: String, enabled: Boolean, change: (String) -> Unit) {
    OutlinedTextField(value, { change(it.take(24)) }, Modifier.fillMaxWidth(), label = { Text(label) }, prefix = { Text("HK$") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = enabled)
}
