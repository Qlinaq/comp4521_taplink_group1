package com.taplink.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taplink.app.R
import com.taplink.app.domain.auth.AuthRules
import com.taplink.app.ui.Notice
import com.taplink.app.ui.Panel
import com.taplink.app.ui.PrimaryButton
import com.taplink.app.ui.SectionHeading

/** Exposes the auth view model to screens that are already inside the wallet shell. */
val LocalAuthViewModel = androidx.compose.runtime.staticCompositionLocalOf<AuthViewModel?> { null }

/**
 * Account entry in the leading app-bar slot of the wallet shell.
 *
 * It shows the signed-in name's first character so the screen makes it obvious *whose* wallet is
 * open: since wallets are scoped per account, two accounts on one device do not show the same
 * balance. The guest wallet shows a person glyph instead of a letter.
 */
@Composable
fun AccountEntryButton(onClick: () -> Unit) {
    val vm = LocalAuthViewModel.current ?: return
    val state by vm.state.collectAsStateWithLifecycle()
    val account = state.account
    // `take(1)` keeps a CJK name readable: a single character is a label, two wrap the circle.
    val initial = account?.displayName?.trim()?.take(1)?.uppercase()?.takeIf { it.isNotBlank() }
    val label = if (account != null) stringResource(R.string.account_open_named, account.displayName)
        else stringResource(R.string.account_open)
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (initial != null) {
                Text(initial, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            } else {
                Icon(Icons.Outlined.Person, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

/**
 * Account block inside Settings. Signed in, it offers password change, sign-out and deletion. In the
 * demo wallet it explains the scope and offers the upgrade path to an account.
 */
@Composable
fun AccountSection(showHeading: Boolean = true) {
    val vm = LocalAuthViewModel.current ?: return
    val state by vm.state.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val guest by vm.guest.collectAsStateWithLifecycle()
    val account = state.account

    LaunchedEffect(session) { vm.refreshAccount() }

    val failure = state.error?.let { stringResource(errorRes(it)) }
    val issue = state.issue?.let { stringResource(issueRes(it)) }
    val notice = state.notice?.let { stringResource(noticeRes(it)) }

    var changingPassword by rememberSaveable { mutableStateOf(false) }

    // A successful change collapses the form instead of leaving stale values on screen.
    LaunchedEffect(state.notice) {
        if (state.notice == AuthNotice.PASSWORD_CHANGED) changingPassword = false
    }

    Panel {
        // The dedicated account screen already has a page heading; Settings needs this one.
        if (showHeading) SectionHeading(stringResource(R.string.account_heading))
        when {
            session != null && account != null -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(
                            Icons.Outlined.Badge,
                            null,
                            Modifier.padding(10.dp).size(22.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(account.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            AuthRules.maskIdentifier(account.email ?: account.phone.orEmpty()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Outlined.VerifiedUser,
                        null,
                        Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    stringResource(if (account.email != null) R.string.account_email_verified else R.string.account_phone_verified),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                failure?.let { Notice(it, true) }
                issue?.let { Notice(it, true) }
                notice?.let { Notice(it) }

                if (changingPassword) {
                    ChangePasswordForm(vm) { changingPassword = false }
                } else {
                    OutlinedButton(
                        onClick = { vm.clearFeedback(); changingPassword = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.busy
                    ) { Text(stringResource(R.string.account_change_password)) }
                }
                TextButton(onClick = vm::signOut, modifier = Modifier.fillMaxWidth(), enabled = !state.busy) {
                    Text(stringResource(R.string.account_sign_out))
                }
                TextButton(
                    onClick = vm::beginDeleteAccount,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.busy
                ) {
                    Text(
                        stringResource(R.string.account_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            guest -> {
                Text(stringResource(R.string.account_guest_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.account_guest_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                notice?.let { Notice(it) }
                PrimaryButton(stringResource(R.string.account_guest_action), !state.busy, state.busy) {
                    vm.startAccountFromGuest()
                }
            }

            else -> {
                Text(
                    stringResource(R.string.auth_sign_in_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PrimaryButton(stringResource(R.string.auth_sign_in), !state.busy, state.busy) {
                    vm.show(AuthScreen.SIGN_IN)
                }
            }
        }
    }

    if (state.deleting) {
        DeleteAccountDialog(vm, failure, issue)
    }
}

@Composable
private fun ChangePasswordForm(vm: AuthViewModel, onDone: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var current by rememberSaveable { mutableStateOf("") }
    var next by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AuthField(
            label = stringResource(R.string.account_current_password),
            value = current,
            onValueChange = { current = it },
            password = true
        )
        AuthField(
            label = stringResource(R.string.auth_new_password_label),
            value = next,
            onValueChange = { next = it },
            password = true
        )
        AuthField(
            label = stringResource(R.string.auth_confirm_password_label),
            value = confirm,
            onValueChange = { confirm = it },
            password = true,
            imeAction = ImeAction.Done
        )
        Text(
            stringResource(R.string.auth_password_rules),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onDone, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.account_cancel))
            }
            Button(
                onClick = { vm.changePassword(current, next, confirm) },
                modifier = Modifier.weight(1f),
                enabled = !state.busy
            ) { Text(stringResource(R.string.account_change_password)) }
        }
    }
}

@Composable
private fun DeleteAccountDialog(vm: AuthViewModel, failure: String?, issue: String?) {
    val state by vm.state.collectAsStateWithLifecycle()
    var password by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = vm::cancelDeleteAccount,
        title = { Text(stringResource(R.string.account_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.account_delete_body))
                failure?.let { Notice(it, true) }
                issue?.let { Notice(it, true) }
                AuthField(
                    label = stringResource(R.string.auth_password_label),
                    value = password,
                    onValueChange = { password = it },
                    password = true,
                    imeAction = ImeAction.Done
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.deleteAccount(password) }, enabled = !state.busy) {
                Text(stringResource(R.string.account_delete_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = vm::cancelDeleteAccount) { Text(stringResource(R.string.account_cancel)) }
        }
    )
}
