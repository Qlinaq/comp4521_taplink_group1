package com.taplink.app.domain.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Delivers a one-time code. The interface exists so the transport is a decision, not a habit: the
 * course prototype uses [SimulatedVerificationSender], while a production build would implement
 * this against a backend that owns the mail or SMS credentials. No provider secret belongs in the
 * APK.
 */
interface VerificationSender {
    val simulated: Boolean
    suspend fun send(delivery: CodeDelivery): AuthResult<Unit>
}

/**
 * Records the code in memory so the UI can display it in a clearly labelled demo inbox, and returns
 * success immediately. Nothing leaves the device, so the demo works offline and has no setup step.
 */
class SimulatedVerificationSender : VerificationSender {
    private val _lastDelivery = MutableStateFlow<CodeDelivery?>(null)

    /** The most recent delivery, shown by the demo inbox. Never persisted and never logged. */
    val lastDelivery: StateFlow<CodeDelivery?> = _lastDelivery.asStateFlow()

    override val simulated: Boolean = true

    override suspend fun send(delivery: CodeDelivery): AuthResult<Unit> {
        _lastDelivery.value = delivery
        return AuthResult.Success(Unit)
    }

    fun clear() {
        _lastDelivery.value = null
    }
}
