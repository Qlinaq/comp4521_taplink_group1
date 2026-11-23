package com.taplink.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taplink.app.R
import com.taplink.app.ui.*

/**
 * Octopus-style notification settings: one master switch, then the categories a user actually
 * distinguishes. The in-app inbox always keeps every message; these switches only decide whether the
 * phone also raises an alert.
 */
@Composable
fun NotificationSettingsScreen(vm: WalletViewModel, platform: PlatformActions) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val allowed = platform.notificationsAllowed?.value
    val master = prefs.notificationsEnabled

    // Opening the screen always re-reads the permission, so a change made in Android settings is
    // reflected even when the activity never left the foreground.
    LaunchedEffect(Unit) { platform.refreshNotificationState?.invoke() }

    ScreenColumn {
        PageHeading(
            stringResource(R.string.notif_eyebrow),
            stringResource(R.string.notif_title),
            stringResource(R.string.notif_subtitle)
        )

        Panel {
            SectionHeading(stringResource(R.string.notif_section_master))
            PreferenceSwitch(
                stringResource(R.string.notif_master_title),
                stringResource(R.string.notif_master_body),
                master,
                !busy
            ) { checked -> vm.updatePreferences { it.copy(notificationsEnabled = checked) } }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (allowed == true) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                    null,
                    Modifier.size(20.dp),
                    tint = if (allowed == true) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(if (allowed == true) R.string.notif_permission_allowed else R.string.notif_permission_blocked),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            platform.openNotificationSettings?.let { open ->
                OutlinedButton(open, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.notif_open_system_settings))
                }
            }
            platform.sendTestNotification?.let { send ->
                OutlinedButton(send, Modifier.fillMaxWidth(), enabled = master) {
                    Icon(Icons.Outlined.Send, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.notif_send_test))
                }
            }
            if (allowed == false) HelperText(stringResource(R.string.notif_blocked_notice))
        }

        Panel {
            SectionHeading(stringResource(R.string.notif_section_categories))
            CategorySwitch(
                R.string.notif_category_transactions,
                R.string.notif_category_transactions_body,
                prefs.transactionAlerts,
                master && !busy
            ) { checked -> vm.updatePreferences { it.copy(transactionAlerts = checked) } }
            CategorySwitch(
                R.string.notif_category_low_balance,
                R.string.notif_category_low_balance_body,
                prefs.lowBalanceAlerts,
                master && !busy
            ) { checked -> vm.updatePreferences { it.copy(lowBalanceAlerts = checked) } }
            CategorySwitch(
                R.string.notif_category_pass_expiry,
                R.string.notif_category_pass_expiry_body,
                prefs.expiryRemindersEnabled,
                master && !busy
            ) { checked -> vm.updatePreferences { it.copy(expiryRemindersEnabled = checked) } }
            CategorySwitch(
                R.string.notif_category_offers,
                R.string.notif_category_offers_body,
                prefs.offerAlerts,
                master && !busy
            ) { checked -> vm.updatePreferences { it.copy(offerAlerts = checked) } }
            CategorySwitch(
                R.string.notif_category_service,
                R.string.notif_category_service_body,
                prefs.serviceAnnouncements,
                master && !busy
            ) { checked -> vm.updatePreferences { it.copy(serviceAnnouncements = checked) } }
        }

        Panel {
            SectionHeading(stringResource(R.string.notif_section_how))
            Text(stringResource(R.string.notif_how_body), style = MaterialTheme.typography.bodyMedium)
            HelperText(stringResource(R.string.notif_how_note))
            OutlinedButton({ vm.navigate("Messages") }, Modifier.fillMaxWidth(), enabled = !busy) {
                Icon(Icons.Outlined.Inbox, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.notif_open_inbox))
            }
        }

        Panel {
            SectionHeading(stringResource(R.string.notif_section_scheduled))
            PreferenceSwitch(
                stringResource(R.string.notif_scheduled_title),
                stringResource(R.string.notif_scheduled_body),
                prefs.remindersEnabled,
                !busy
            ) { checked -> vm.updatePreferences { it.copy(remindersEnabled = checked) } }
            Text(
                stringResource(R.string.notif_scheduled_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CategorySwitch(
    title: Int,
    body: Int,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    PreferenceSwitch(stringResource(title), stringResource(body), checked, enabled, onChange)
}
