package com.taplink.app.ui.support

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.taplink.app.BuildConfig
import com.taplink.app.R
import com.taplink.app.data.support.DeepSeekSupport
import com.taplink.app.data.support.FaqTopic
import com.taplink.app.data.support.FailureKind
import com.taplink.app.data.support.SharedPreferencesUsageStore
import com.taplink.app.data.support.SupportFacts
import com.taplink.app.data.support.SupportQuota
import com.taplink.app.domain.Money
import com.taplink.app.domain.SupportContacts
import com.taplink.app.platform.KeyboardSupport
import com.taplink.app.ui.*
import com.taplink.app.ui.theme.LocalHighContrast

/**
 * Real-time text support.
 *
 * The assistant answers from DeepSeek when a key is configured; otherwise, or when the network or
 * the daily allowance fails, the same screen answers from a small offline knowledge base and offers
 * WhatsApp. A failure is always shown as a failure, never disguised as an answer.
 */
@Composable
fun SupportChatScreen(wallet: WalletViewModel, platform: PlatformActions) {
    val context = LocalContext.current
    val prefs by wallet.preferences.collectAsStateWithLifecycle()
    val selected by wallet.selectedCard.collectAsStateWithLifecycle()
    val passes by wallet.passes.collectAsStateWithLifecycle()

    val support: SupportChatViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val quota = SupportQuota(SharedPreferencesUsageStore(context))
                SupportChatViewModel(
                    DeepSeekSupport(
                        apiKey = BuildConfig.DEEPSEEK_API_KEY,
                        model = BuildConfig.DEEPSEEK_MODEL,
                        quota = quota
                    )
                )
            }
        }
    )
    val messages by support.messages.collectAsStateWithLifecycle()
    val sending by support.sending.collectAsStateWithLifecycle()
    val remaining by support.remaining.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    // A device can be set to Chinese while every installed keyboard only has a Latin layout; the
    // field then accepts nothing the user can type. Checked again on resume, because the fix is to
    // leave the app and add a keyboard.
    val lifecycleOwner = LocalLifecycleOwner.current
    var canType by remember { mutableStateOf(KeyboardSupport.canType(context, prefs.language)) }
    DisposableEffect(lifecycleOwner, prefs.language) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) canType = KeyboardSupport.canType(context, prefs.language)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The balance is only shared when it is visible on screen, so read-aloud and assistant context
    // cannot disclose a value the user chose to hide.
    val facts = remember(prefs, selected, passes) {
        SupportFacts(
            cardName = selected?.name,
            balance = selected?.balanceCents?.takeIf { prefs.balanceVisible }?.let(Money::format),
            lowBalanceThreshold = Money.format(prefs.lowBalanceThresholdCents),
            language = prefs.language,
            signedIn = false,
            activePass = passes.any { it.isActive() }
        )
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
            // Only a status line here: the app bar already carries the screen title, and a full
            // heading left no room for the conversation at 150% text.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModeBanner(stringResource(R.string.support_badge_ai))
                if (support.configured) {
                    Text(
                        stringResource(R.string.support_remaining, remaining),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                // Bottom padding is larger than the top so the footer's last line never sits flush
                // against the pinned input row, where it reads as a clipped sentence.
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!support.configured) {
                    item { Notice(stringResource(R.string.support_not_configured)) }
                }
                if (messages.isEmpty()) {
                    item { SupportIntro(platform, prefs.elderlyMode) { question -> support.send(question, facts) } }
                }
                items(messages, key = { it.id }) { message -> ChatBubble(message, sending) }
                if (messages.isNotEmpty() && !sending) {
                    item { SuggestionRow { question -> support.send(question, facts) } }
                }
                item {
                    SupportFooter(
                        platform = platform,
                        canClear = messages.isNotEmpty(),
                        onClear = { support.clear() }
                    )
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!canType) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                stringResource(R.string.support_keyboard_missing),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            platform.openKeyboardSettings?.let { open ->
                                TextButton({ open() }) { Text(stringResource(R.string.support_keyboard_settings)) }
                            }
                        }
                    }
                }
                HelperText(stringResource(R.string.support_privacy))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(500) },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.support_input_hint)) },
                        maxLines = 4,
                        enabled = !sending
                    )
                    FilledIconButton(
                        onClick = { if (draft.isNotBlank()) { support.send(draft, facts); draft = "" } },
                        enabled = draft.isNotBlank() && !sending,
                        modifier = Modifier.size(52.dp)
                    ) { Icon(Icons.AutoMirrored.Outlined.Send, stringResource(R.string.support_send)) }
                }
            }
        }
    }
}

/**
 * Handoff and disclaimer sit at the end of the conversation rather than pinned under the input:
 * on a small screen at 150% text, pinned controls left almost no room for messages.
 */
@Composable
private fun SupportFooter(platform: PlatformActions, canClear: Boolean, onClear: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (canClear) {
                TextButton(onClick = onClear) { Text(stringResource(R.string.support_clear)) }
            }
            Spacer(Modifier.weight(1f))
            platform.openWhatsApp?.let { open ->
                val prefill = stringResource(R.string.contact_whatsapp_message)
                TextButton({ open(prefill) }) {
                    Icon(Icons.AutoMirrored.Outlined.Chat, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.support_handoff, SupportContacts.WHATSAPP_DISPLAY))
                }
            }
        }
        Text(
            stringResource(R.string.support_ai_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SupportIntro(platform: PlatformActions, elderly: Boolean, onPick: (String) -> Unit) {
    Panel {
        Text(stringResource(R.string.support_intro_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.support_subtitle), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.support_intro_body), style = MaterialTheme.typography.bodyMedium)
        SuggestionRow(onPick)
        if (elderly) HelperText(stringResource(R.string.support_intro_elderly))
        platform.openWhatsApp?.let { open ->
            val prefill = stringResource(R.string.contact_whatsapp_message)
            OutlinedButton({ open(prefill) }, Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Outlined.Chat, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.contact_whatsapp))
            }
        }
    }
}

/** Quick questions double as a fallback when the microphone-free chat is hard to type into. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionRow(onPick: (String) -> Unit) {
    val questions = listOf(
        R.string.support_q_topup,
        R.string.support_q_pass,
        R.string.support_q_password,
        R.string.support_q_accessibility
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        questions.forEach { res ->
            val text = stringResource(res)
            AssistChip(onClick = { onPick(text) }, label = { Text(text) })
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, sending: Boolean) {
    val fromUser = message.fromUser
    val container = when {
        message.blocked -> MaterialTheme.colorScheme.errorContainer
        fromUser -> MaterialTheme.colorScheme.primaryContainer
        // The assistant bubble uses surfaceVariant: on a white sheet a `surface` bubble would be
        // invisible, which happens in the high-contrast theme.
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val outline = if (LocalHighContrast.current) Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)) else Modifier
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = container,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.widthIn(max = 520.dp).then(outline)
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    message.blocked -> Text(
                        stringResource(R.string.support_blocked),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    message.pending -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.support_sending), style = MaterialTheme.typography.bodyMedium)
                    }
                    message.faq != null -> {
                        Text(
                            stringResource(R.string.support_faq_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(faqBody(message.faq), style = MaterialTheme.typography.bodyMedium)
                        message.problem?.let { problem ->
                            if (problem != FailureKind.EMPTY) {
                                Text(
                                    stringResource(problemText(problem)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    else -> Text(message.text.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun problemText(kind: FailureKind): Int = when (kind) {
    FailureKind.NOT_CONFIGURED -> R.string.support_not_configured
    FailureKind.OFFLINE -> R.string.support_offline
    FailureKind.QUOTA_REACHED -> R.string.support_quota_reached
    FailureKind.UNAUTHORIZED -> R.string.support_unauthorized
    FailureKind.RATE_LIMITED -> R.string.support_rate_limited
    FailureKind.UNAVAILABLE, FailureKind.EMPTY -> R.string.support_unavailable
}

/** Offline answers follow the interface language and never claim to come from the model. */
@Composable
private fun faqBody(topic: FaqTopic): String = stringResource(
    when (topic) {
        FaqTopic.TOP_UP -> R.string.faq_topup
        FaqTopic.PAY -> R.string.faq_pay
        FaqTopic.BALANCE -> R.string.faq_balance
        FaqTopic.PASS -> R.string.faq_pass
        FaqTopic.PASSWORD -> R.string.faq_password
        FaqTopic.FREEZE -> R.string.faq_freeze
        FaqTopic.REFUND -> R.string.faq_refund
        FaqTopic.LANGUAGE -> R.string.faq_language
        FaqTopic.ACCESSIBILITY -> R.string.faq_accessibility
        FaqTopic.NOTIFICATIONS -> R.string.faq_notifications
        FaqTopic.SIGN_LANGUAGE -> R.string.faq_sign_language
        FaqTopic.UNKNOWN -> R.string.support_faq_not_matched
    }
)
