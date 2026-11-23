package com.taplink.app.ui.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taplink.app.R
import com.taplink.app.domain.auth.AuthError
import com.taplink.app.domain.auth.AuthRules
import com.taplink.app.ui.Notice
import com.taplink.app.ui.PageHeading
import com.taplink.app.ui.Panel
import com.taplink.app.ui.PrimaryButton
import com.taplink.app.ui.ScreenColumn

@StringRes
fun errorRes(error: AuthError): Int = when (error) {
    AuthError.INVALID_IDENTIFIER -> R.string.err_invalid_identifier
    AuthError.INVALID_NAME -> R.string.err_invalid_name
    AuthError.IDENTIFIER_TAKEN -> R.string.err_identifier_taken
    AuthError.INVALID_PASSWORD -> R.string.err_invalid_password
    AuthError.WRONG_CREDENTIALS -> R.string.err_wrong_credentials
    AuthError.ACCOUNT_LOCKED -> R.string.err_account_locked
    AuthError.ACCOUNT_NOT_FOUND -> R.string.err_account_not_found
    AuthError.NOT_VERIFIED -> R.string.err_not_verified
    AuthError.CODE_INVALID -> R.string.err_code_invalid
    AuthError.CODE_EXPIRED -> R.string.err_code_expired
    AuthError.CODE_ALREADY_USED -> R.string.err_code_expired
    AuthError.TOO_MANY_ATTEMPTS -> R.string.err_too_many_attempts
    AuthError.RESEND_TOO_SOON -> R.string.err_resend_too_soon
    AuthError.SESSION_EXPIRED -> R.string.err_session_expired
    AuthError.STORAGE_ERROR -> R.string.err_storage
}

@StringRes
fun issueRes(issue: FormIssue): Int = when (issue) {
    FormIssue.MISSING_FIELDS -> R.string.issue_missing_fields
    FormIssue.PASSWORD_MISMATCH -> R.string.issue_password_mismatch
}

@StringRes
fun noticeRes(notice: AuthNotice): Int = when (notice) {
    AuthNotice.CODE_SENT -> R.string.notice_code_sent
    AuthNotice.SIGNED_OUT -> R.string.notice_signed_out
    AuthNotice.PASSWORD_CHANGED -> R.string.notice_password_changed
    AuthNotice.PASSWORD_RESET -> R.string.notice_password_reset
    AuthNotice.ACCOUNT_DELETED -> R.string.notice_account_deleted
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthFlow(vm: AuthViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val failure = state.error?.let { stringResource(errorRes(it)) }
    val issue = state.issue?.let { stringResource(issueRes(it)) }
    val notice = state.notice?.let { stringResource(noticeRes(it)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (state.screen != AuthScreen.WELCOME) {
                        IconButton(onClick = vm::back, enabled = !state.busy) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(Modifier.padding(padding).imePadding().fillMaxSize()) {
            when (state.screen) {
                AuthScreen.WELCOME -> WelcomeScreen(vm, notice)
                AuthScreen.SIGN_IN -> SignInScreen(vm, failure, issue, notice)
                AuthScreen.SIGN_UP -> SignUpScreen(vm, failure, issue)
                AuthScreen.VERIFY -> VerifyScreen(vm, failure, issue, notice, state)
                AuthScreen.RESET_REQUEST -> ResetRequestScreen(vm, failure, issue)
                AuthScreen.RESET_CONFIRM -> ResetConfirmScreen(vm, failure, issue, notice, state)
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun WelcomeScreen(vm: AuthViewModel, notice: String?) {
    ScreenColumn {
        PageHeading(
            eyebrow = stringResource(R.string.auth_welcome_eyebrow),
            title = stringResource(R.string.auth_welcome_title),
            subtitle = stringResource(R.string.auth_welcome_subtitle)
        )
        Panel {
            PrimaryButton(stringResource(R.string.auth_sign_in)) { vm.show(AuthScreen.SIGN_IN) }
            OutlinedButton(
                onClick = { vm.show(AuthScreen.SIGN_UP) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text(stringResource(R.string.auth_create_account)) }
            HorizontalDivider()
            Text(
                stringResource(R.string.auth_guest_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = vm::continueAsGuest, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.auth_continue_guest))
            }
        }
        notice?.let { Notice(it) }
    }
}

@Composable
private fun SignInScreen(vm: AuthViewModel, failure: String?, issue: String?, notice: String?) {
    var identifier by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    ScreenColumn {
        PageHeading(
            eyebrow = stringResource(R.string.auth_sign_in_eyebrow),
            title = stringResource(R.string.auth_sign_in_title),
            subtitle = stringResource(R.string.auth_sign_in_subtitle)
        )
        notice?.let { Notice(it) }
        failure?.let { Notice(it, true) }
        issue?.let { Notice(it, true) }
        Panel {
            AuthField(
                label = stringResource(R.string.auth_identifier_label),
                value = identifier,
                onValueChange = { identifier = it },
                placeholder = stringResource(R.string.auth_identifier_placeholder),
                keyboard = KeyboardType.Email
            )
            AuthField(
                label = stringResource(R.string.auth_password_label),
                value = password,
                onValueChange = { password = it },
                password = true,
                imeAction = ImeAction.Done
            )
            TextButton(onClick = { vm.show(AuthScreen.RESET_REQUEST) }) {
                Text(stringResource(R.string.auth_forgot_password))
            }
            PrimaryButton(stringResource(R.string.auth_sign_in_submit), busy = vm.state.collectAsStateWithLifecycle().value.busy) {
                vm.signIn(identifier, password)
            }
        }
        TextButton(onClick = { vm.show(AuthScreen.SIGN_UP) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.auth_no_account_yet))
        }
    }
}

@Composable
private fun SignUpScreen(vm: AuthViewModel, failure: String?, issue: String?) {
    var name by rememberSaveable { mutableStateOf("") }
    var identifier by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }

    ScreenColumn {
        PageHeading(
            eyebrow = stringResource(R.string.auth_sign_up_eyebrow),
            title = stringResource(R.string.auth_sign_up_title),
            subtitle = stringResource(R.string.auth_sign_up_subtitle)
        )
        failure?.let { Notice(it, true) }
        issue?.let { Notice(it, true) }
        Panel {
            AuthField(
                label = stringResource(R.string.auth_name_label),
                value = name,
                onValueChange = { if (it.length <= 60) name = it },
                leading = Icons.Outlined.Person
            )
            AuthField(
                label = stringResource(R.string.auth_identifier_label),
                value = identifier,
                onValueChange = { identifier = it },
                placeholder = stringResource(R.string.auth_identifier_placeholder),
                keyboard = KeyboardType.Email
            )
            AuthField(
                label = stringResource(R.string.auth_password_label),
                value = password,
                onValueChange = { password = it },
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
            PrimaryButton(stringResource(R.string.auth_sign_up_submit)) {
                vm.startSignUp(name, identifier, password, confirm)
            }
        }
        Notice(stringResource(R.string.auth_sign_up_terms))
        TextButton(onClick = { vm.show(AuthScreen.SIGN_IN) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.auth_have_account))
        }
    }
}

@Composable
private fun VerifyScreen(
    vm: AuthViewModel,
    failure: String?,
    issue: String?,
    notice: String?,
    state: AuthUiState
) {
    var code by rememberSaveable { mutableStateOf("") }
    val masked = remember(state.identifier) { AuthRules.normalizeIdentifier(state.identifier)?.second }
        ?.let { AuthRules.maskIdentifier(it) } ?: state.identifier

    ScreenColumn {
        PageHeading(
            eyebrow = stringResource(R.string.auth_verify_eyebrow),
            title = stringResource(R.string.auth_verify_title),
            subtitle = stringResource(R.string.auth_verify_subtitle, masked)
        )
        notice?.let { Notice(it) }
        failure?.let { Notice(it, true) }
        issue?.let { Notice(it, true) }
        state.delivery?.let { delivery ->
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.MarkEmailRead, null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.auth_demo_inbox_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    if (delivery.simulated) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                stringResource(R.string.auth_simulated_badge),
                                Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                Text(
                    delivery.code,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 8.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(R.string.auth_demo_inbox_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.auth_code_expires),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Panel {
            AuthField(
                label = stringResource(R.string.auth_code_label),
                value = code,
                onValueChange = { input -> code = input.filter { it in '0'..'9' }.take(6) },
                keyboard = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            )
            PrimaryButton(stringResource(R.string.auth_verify_submit)) { vm.confirmSignUp(code) }
            TextButton(onClick = vm::resendSignUpCode, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.auth_resend_code))
            }
        }
    }
}

@Composable
private fun ResetRequestScreen(vm: AuthViewModel, failure: String?, issue: String?) {
    var identifier by rememberSaveable { mutableStateOf("") }

    ScreenColumn {
        PageHeading(
            eyebrow = stringResource(R.string.auth_reset_eyebrow),
            title = stringResource(R.string.auth_reset_title),
            subtitle = stringResource(R.string.auth_reset_subtitle)
        )
        failure?.let { Notice(it, true) }
        issue?.let { Notice(it, true) }
        Panel {
            AuthField(
                label = stringResource(R.string.auth_identifier_label),
                value = identifier,
                onValueChange = { identifier = it },
                placeholder = stringResource(R.string.auth_identifier_placeholder),
                keyboard = KeyboardType.Email,
                imeAction = ImeAction.Done
            )
            PrimaryButton(stringResource(R.string.auth_reset_send)) { vm.requestReset(identifier) }
        }
    }
}

@Composable
private fun ResetConfirmScreen(
    vm: AuthViewModel,
    failure: String?,
    issue: String?,
    notice: String?,
    state: AuthUiState
) {
    var code by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    val masked = remember(state.identifier) { AuthRules.normalizeIdentifier(state.identifier)?.second }
        ?.let { AuthRules.maskIdentifier(it) } ?: state.identifier

    ScreenColumn {
        PageHeading(
            eyebrow = stringResource(R.string.auth_reset_confirm_eyebrow),
            title = stringResource(R.string.auth_reset_confirm_title),
            subtitle = stringResource(R.string.auth_reset_confirm_subtitle, masked)
        )
        notice?.let { Notice(it) }
        failure?.let { Notice(it, true) }
        issue?.let { Notice(it, true) }
        state.delivery?.let { delivery ->
            Panel {
                Text(
                    delivery.code,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 8.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(R.string.auth_demo_inbox_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Panel {
            AuthField(
                label = stringResource(R.string.auth_code_label),
                value = code,
                onValueChange = { input -> code = input.filter { it in '0'..'9' }.take(6) },
                keyboard = KeyboardType.NumberPassword
            )
            AuthField(
                label = stringResource(R.string.auth_new_password_label),
                value = password,
                onValueChange = { password = it },
                password = true,
                leading = Icons.Outlined.Lock
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
            PrimaryButton(stringResource(R.string.auth_reset_submit)) { vm.confirmReset(code, password, confirm) }
            TextButton(onClick = vm::resendResetCode, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.auth_resend_code))
            }
        }
    }
}

@Composable
fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    leading: androidx.compose.ui.graphics.vector.ImageVector? = null,
    enabled: Boolean = true
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leading?.let { { Icon(it, null) } },
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard, imeAction = imeAction),
        trailingIcon = if (password) {
            {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        stringResource(if (visible) R.string.auth_password_hide else R.string.auth_password_show)
                    )
                }
            }
        } else null,
        shape = MaterialTheme.shapes.medium
    )
}
