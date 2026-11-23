package com.taplink.app.ui.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taplink.app.data.support.AssistantResult
import com.taplink.app.data.support.DeepSeekSupport
import com.taplink.app.data.support.FaqMatcher
import com.taplink.app.data.support.FaqTopic
import com.taplink.app.data.support.FailureKind
import com.taplink.app.data.support.SupportFacts
import com.taplink.app.data.support.SupportTurn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String? = null,
    /** Set when the reply comes from the offline knowledge base instead of the model. */
    val faq: FaqTopic? = null,
    val problem: FailureKind? = null,
    val pending: Boolean = false,
    val blocked: Boolean = false
)

/**
 * Chat state for the support screen.
 *
 * The model is optional: with no key, no network or a spent daily allowance the screen still
 * answers from a small offline knowledge base and offers WhatsApp. A reply is never invented when
 * the model fails; the failure is shown as such.
 */
class SupportChatViewModel(private val assistant: DeepSeekSupport) : ViewModel() {
    val messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val sending = MutableStateFlow(false)
    val remaining = MutableStateFlow(0)
    val configured = assistant.isConfigured
    private var nextId = 1L

    init { refreshQuota() }

    fun refreshQuota() { remaining.value = runCatching { assistant.remaining() }.getOrDefault(0) }

    fun send(raw: String, facts: SupportFacts) {
        val text = raw.trim().take(MAX_INPUT)
        if (text.isEmpty() || sending.value) return
        if (looksLikeSecret(text)) {
            append(ChatMessage(nextId++, fromUser = true, text = text))
            append(ChatMessage(nextId++, fromUser = false, blocked = true))
            return
        }
        append(ChatMessage(nextId++, fromUser = true, text = text))
        val pending = ChatMessage(nextId++, fromUser = false, pending = true)
        append(pending)
        sending.value = true
        viewModelScope.launch {
            try {
                val turns = messages.value
                    .filter { !it.pending && !it.blocked && it.text != null }
                    .map { SupportTurn(fromUser = it.fromUser, text = it.text!!) }
                when (val result = assistant.answer(facts, turns)) {
                    is AssistantResult.Answer -> replace(pending.id, ChatMessage(pending.id, false, text = result.text))
                    is AssistantResult.Failure -> replace(
                        pending.id,
                        ChatMessage(pending.id, false, faq = FaqMatcher.match(text), problem = result.kind)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                replace(pending.id, ChatMessage(pending.id, false, faq = FaqMatcher.match(text), problem = FailureKind.UNAVAILABLE))
            } finally {
                sending.value = false
                refreshQuota()
            }
        }
    }

    fun clear() {
        if (sending.value) return
        messages.value = emptyList()
    }

    private fun append(message: ChatMessage) { messages.value = messages.value + message }

    private fun replace(id: Long, message: ChatMessage) {
        messages.value = messages.value.map { if (it.id == id) message else it }
    }

    private fun looksLikeSecret(text: String): Boolean = SECRET_DIGITS.containsMatchIn(text) ||
        SECRET_WORDS.any { text.lowercase().contains(it) } && text.any(Char::isDigit)

    private companion object {
        const val MAX_INPUT = 500
        val SECRET_DIGITS = Regex("\\d{6,}")
        val SECRET_WORDS = listOf("驗證碼", "验证码", "otp", "password", "密碼", "密码")
    }
}
