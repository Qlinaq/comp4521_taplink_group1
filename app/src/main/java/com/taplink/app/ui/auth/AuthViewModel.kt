package com.taplink.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taplink.app.data.auth.RoomAuthRepository
import com.taplink.app.domain.auth.Account
import com.taplink.app.domain.auth.AuthError
import com.taplink.app.domain.auth.AuthResult
import com.taplink.app.domain.auth.CodeDelivery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthScreen { WELCOME, SIGN_IN, SIGN_UP, VERIFY, RESET_REQUEST, RESET_CONFIRM }

/** Field-level problems the UI can point at directly. */
enum class FormIssue { MISSING_FIELDS, PASSWORD_MISMATCH }

/** Success feedback. Worded by the UI layer so it can be localised. */
enum class AuthNotice { CODE_SENT, SIGNED_OUT, PASSWORD_CHANGED, PASSWORD_RESET, ACCOUNT_DELETED }

data class AuthUiState(
    val screen: AuthScreen = AuthScreen.WELCOME,
    val busy: Boolean = false,
    val error: AuthError? = null,
    val issue: FormIssue? = null,
    val notice: AuthNotice? = null,
    val identifier: String = "",
    val displayName: String = "",
    val delivery: CodeDelivery? = null,
    val account: Account? = null,
    val deleting: Boolean = false
)

class AuthViewModel(private val repository: RoomAuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    val session = repository.session
    val guest = repository.guest
    val simulatedDelivery: Boolean get() = repository.simulatedDelivery

    init {
        viewModelScope.launch { refreshAccount() }
    }

    fun show(screen: AuthScreen) {
        _state.update { it.copy(screen = screen, error = null, issue = null, notice = null) }
    }

    fun back() {
        val target = when (_state.value.screen) {
            AuthScreen.WELCOME -> AuthScreen.WELCOME
            AuthScreen.SIGN_IN, AuthScreen.SIGN_UP -> AuthScreen.WELCOME
            AuthScreen.VERIFY -> AuthScreen.SIGN_UP
            AuthScreen.RESET_REQUEST, AuthScreen.RESET_CONFIRM -> AuthScreen.SIGN_IN
        }
        show(target)
    }

    fun clearFeedback() {
        _state.update { it.copy(error = null, issue = null, notice = null) }
    }

    fun continueAsGuest() {
        repository.continueAsGuest()
        clearFeedback()
    }

    /** Leaving the demo wallet lands on the sign-up screen, which is the usual intent. */
    fun startAccountFromGuest() {
        repository.leaveGuest()
        show(AuthScreen.SIGN_UP)
    }

    fun startSignUp(displayName: String, identifier: String, password: String, confirm: String) {
        _state.update { it.copy(error = null, issue = null, notice = null) }
        if (displayName.isBlank() || identifier.isBlank() || password.isEmpty()) {
            _state.update { it.copy(issue = FormIssue.MISSING_FIELDS) }
            return
        }
        if (password != confirm) {
            _state.update { it.copy(issue = FormIssue.PASSWORD_MISMATCH) }
            return
        }
        launch {
            when (val result = repository.startSignUp(displayName, identifier, password)) {
                is AuthResult.Success -> _state.update {
                    it.copy(
                        screen = AuthScreen.VERIFY,
                        identifier = identifier.trim(),
                        displayName = displayName.trim(),
                        delivery = repository.demoDelivery.value,
                        notice = AuthNotice.CODE_SENT
                    )
                }
                is AuthResult.Failure -> _state.update { it.copy(error = result.code) }
            }
        }
    }

    fun confirmSignUp(code: String) {
        _state.update { it.copy(error = null, issue = null) }
        if (code.isBlank()) {
            _state.update { it.copy(issue = FormIssue.MISSING_FIELDS) }
            return
        }
        launch {
            when (val result = repository.confirmSignUp(_state.value.identifier, code)) {
                is AuthResult.Success -> {
                    _state.update { it.copy(screen = AuthScreen.WELCOME, delivery = null, notice = null) }
                    refreshAccount()
                }
                is AuthResult.Failure -> _state.update { it.copy(error = result.code) }
            }
        }
    }

    fun resendSignUpCode() {
        _state.update { it.copy(error = null) }
        launch {
            when (val result = repository.resendSignUpCode(_state.value.identifier)) {
                is AuthResult.Success -> _state.update {
                    it.copy(delivery = repository.demoDelivery.value, notice = AuthNotice.CODE_SENT)
                }
                is AuthResult.Failure -> _state.update { it.copy(error = result.code) }
            }
        }
    }

    fun signIn(identifier: String, password: String) {
        _state.update { it.copy(error = null, issue = null, notice = null) }
        if (identifier.isBlank() || password.isEmpty()) {
            _state.update { it.copy(issue = FormIssue.MISSING_FIELDS) }
            return
        }
        launch {
            when (val result = repository.signIn(identifier, password)) {
                is AuthResult.Success -> {
                    _state.update { it.copy(screen = AuthScreen.WELCOME) }
                    refreshAccount()
                }
                is AuthResult.Failure -> _state.update { it.copy(error = result.code) }
            }
        }
    }

    fun requestReset(identifier: String) {
        _state.update { it.copy(error = null, issue = null) }
        if (identifier.isBlank()) {
            _state.update { it.copy(issue = FormIssue.MISSING_FIELDS) }
            return
        }
        launch {
            when (val result = repository.requestReset(identifier)) {
                is AuthResult.Success -> _state.update {
                    it.copy(
                        screen = AuthScreen.RESET_CONFIRM,
                        identifier = identifier.trim(),
                        delivery = repository.demoDelivery.value,
                        notice = AuthNotice.CODE_SENT
                    )
                }
                is AuthResult.Failure -> _state.update { it.copy(error = result.code) }
            }
        }
    }

    fun resendResetCode() {
        _state.update { it.copy(error = null) }
        launch {
            when (val result = repository.requestReset(_state.value.identifier)) {
                is AuthResult.Success -> _state.update {
                    it.copy(delivery = repository.demoDelivery.value, notice = AuthNotice.CODE_SENT)
                }
                is AuthResult.Failure -> _state.update { it.copy(error = result.code) }
            }
        }
    }

    fun confirmReset(code: String, newPassword: String, confirm: String) {
        _state.update { it.copy(error = null, issue = null) }
        if (code.isBlank() || newPassword.isEmpty()) {
            _state.update { it.copy(issue = FormIssue.MISSING_FIELDS) }
            return
        }
        if (newPassword != confirm) {
            _state.update { it.copy(issue = FormIssue.PASSWORD_MISMATCH) }
            return
        }
        launch {
            when (val result = repository.confirmReset(_state.value.identifier, code, newPassword)) {
                is AuthResult.Success -> _state.update {
                    it.copy(
                        screen = AuthScreen.SIGN_IN,
                        delivery = null,
                        notice = AuthNotice.PASSWORD_RESET,
                        error = null
                    )
                }
                is AuthResult.Failure -> _state.update { it.copy(error = result.code) }
            }
        }
    }

    /** Called from the account card inside Settings; success keeps the user where they are. */
    fun changePassword(current: String, newPassword: String, confirm: String) {
        _state.update { it.copy(error = null, issue = null, notice = null) }
        val account = _state.value.account
        if (account == null) {
            _state.update { it.copy(error = AuthError.SESSION_EXPIRED) }
            return
        }
        if (current.isEmpty() || newPassword.isEmpty()) {
            _state.update { it.copy(issue = FormIssue.MISSING_FIELDS) }
            return
        }
        if (newPassword != confirm) {
            _state.update { it.copy(issue = FormIssue.PASSWORD_MISMATCH) }
            return
        }
        launch {
            when (val result = repository.changePassword(account.id, current, newPassword)) {
                is AuthResult.Success -> _state.update { it.copy(notice = AuthNotice.PASSWORD_CHANGED) }
                is AuthResult.Failure -> _state.update { it.copy(error = result.code) }
            }
        }
    }

    fun beginDeleteAccount() {
        _state.update { it.copy(deleting = true, error = null, issue = null) }
    }

    fun cancelDeleteAccount() {
        _state.update { it.copy(deleting = false, error = null, issue = null) }
    }

    fun deleteAccount(password: String) {
        _state.update { it.copy(error = null, issue = null) }
        val account = _state.value.account
        if (account == null) {
            _state.update { it.copy(error = AuthError.SESSION_EXPIRED, deleting = false) }
            return
        }
        if (password.isEmpty()) {
            _state.update { it.copy(issue = FormIssue.MISSING_FIELDS) }
            return
        }
        launch {
            when (val result = repository.deleteAccount(account.id, password)) {
                is AuthResult.Success -> _state.update {
                    AuthUiState(screen = AuthScreen.WELCOME, notice = AuthNotice.ACCOUNT_DELETED)
                }
                is AuthResult.Failure -> _state.update { it.copy(error = result.code) }
            }
        }
    }

    fun signOut() {
        repository.signOut()
        _state.value = AuthUiState(screen = AuthScreen.WELCOME, notice = AuthNotice.SIGNED_OUT)
    }

    fun refreshAccount() {
        viewModelScope.launch {
            val account = repository.currentAccount()
            _state.update { it.copy(account = account) }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
