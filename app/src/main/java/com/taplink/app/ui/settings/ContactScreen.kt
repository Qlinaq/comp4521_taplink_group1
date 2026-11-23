package com.taplink.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taplink.app.R
import com.taplink.app.domain.SupportContacts
import com.taplink.app.ui.*

/**
 * Contact channels for a public-service app: a messaging channel first (most users), a hotline for
 * urgent cases, an AI text assistant, and a sign-language section. Nothing dials or sends on its own.
 */
@Composable
fun ContactScreen(vm: WalletViewModel, platform: PlatformActions) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val whatsappMessage = stringResource(R.string.contact_whatsapp_message)

    ScreenColumn {
        PageHeading(
            stringResource(R.string.contact_eyebrow),
            stringResource(R.string.contact_title),
            stringResource(R.string.contact_subtitle)
        )
        VoiceSummary(stringResource(R.string.contact_voice_summary, SupportContacts.WHATSAPP_DISPLAY))

        Panel {
            SectionHeading(stringResource(R.string.contact_section_message))
            ContactChannel(
                icon = Icons.AutoMirrored.Outlined.Chat,
                title = stringResource(R.string.contact_whatsapp),
                detail = stringResource(R.string.contact_whatsapp_detail, SupportContacts.WHATSAPP_DISPLAY),
                action = stringResource(R.string.contact_open_whatsapp)
            ) { platform.openWhatsApp?.invoke(whatsappMessage) ?: vm.navigate("Support") }
            HelperText(stringResource(R.string.contact_whatsapp_note))
            HorizontalDivider()
            ContactChannel(
                icon = Icons.Outlined.Phone,
                title = stringResource(R.string.contact_hotline),
                detail = SupportContacts.HOTLINE_DISPLAY,
                action = stringResource(R.string.contact_call)
            ) { platform.dial?.invoke(SupportContacts.HOTLINE_DIAL) ?: vm.navigate("Support") }
            HelperText(stringResource(R.string.contact_hotline_note))
        }

        Panel {
            SectionHeading(stringResource(R.string.contact_section_online))
            ContactChannel(
                icon = Icons.Outlined.SupportAgent,
                title = stringResource(R.string.contact_chat),
                detail = stringResource(R.string.contact_chat_detail),
                action = stringResource(R.string.contact_chat_action)
            ) { vm.navigate("Support") }
            HorizontalDivider()
            ContactChannel(
                icon = Icons.Outlined.SignLanguage,
                title = stringResource(R.string.contact_sign),
                detail = stringResource(R.string.contact_sign_detail),
                action = stringResource(R.string.contact_view)
            ) { vm.navigate("SignLanguage") }
        }

        Panel {
            SectionHeading(stringResource(R.string.contact_section_hours))
            DetailRow(stringResource(R.string.contact_hours_label), stringResource(R.string.contact_hours_value))
            DetailRow(stringResource(R.string.contact_response_label), stringResource(R.string.contact_response_value))
        }

        Notice(stringResource(R.string.contact_disclaimer))

        OutlinedButton({ vm.navigate("Settings") }, Modifier.fillMaxWidth(), enabled = !busy) {
            Text(stringResource(R.string.contact_back_to_settings))
        }
    }
}

@Composable
private fun ContactChannel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    action: String,
    onClick: () -> Unit
) {
    Surface(onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, Modifier.padding(top = 2.dp).size(26.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 2)
        }
    }
}
