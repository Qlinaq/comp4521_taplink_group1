package com.taplink.app.ui.support

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taplink.app.R
import com.taplink.app.domain.AppLanguage
import com.taplink.app.domain.SupportContacts
import com.taplink.app.ui.*

/**
 * Sign-language support in four chapters: top up, pay, monthly pass, password.
 *
 * The prototype ships without footage, so each chapter is a complete written transcript and the
 * player stays disabled with an explicit "no video attached" label. When the department supplies
 * clips, dropping `<chapter>.mp4` into `assets/sign_language` enables the same screen with no code
 * change. Showing a placeholder animation as if it were sign language would be misleading.
 */
@Composable
fun SignLanguageScreen(vm: WalletViewModel, platform: PlatformActions) {
    val context = LocalContext.current
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val available = remember {
        runCatching { context.assets.list("sign_language")?.toSet().orEmpty() }.getOrDefault(emptySet())
    }
    var playing by remember { mutableStateOf<String?>(null) }

    ScreenColumn {
        PageHeading(
            stringResource(R.string.sign_eyebrow),
            stringResource(R.string.sign_title),
            stringResource(R.string.sign_subtitle)
        )
        VoiceSummary(stringResource(R.string.sign_voice_summary))
        Notice(stringResource(R.string.sign_notice))

        SupportContacts.SIGN_CHAPTERS.forEach { id ->
            val file = "$id.mp4"
            Panel {
                Text(stringResource(signTitle(id)), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(signBody(id)), style = MaterialTheme.typography.bodyMedium)
                if (available.contains(file)) {
                    OutlinedButton({ playing = id }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.PlayCircle, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.sign_play))
                    }
                } else {
                    OutlinedButton({}, Modifier.fillMaxWidth(), enabled = false) {
                        Icon(Icons.Outlined.VideocamOff, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.sign_video_missing))
                    }
                    HelperText(stringResource(R.string.sign_transcript_note))
                }
            }
        }

        Panel {
            SectionHeading(stringResource(R.string.sign_more))
            platform.openUrl?.let { open ->
                OutlinedButton({ open(accessibilityUrl(prefs.language)) }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Language, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sign_accessibility_link))
                }
            }
            Text(stringResource(R.string.sign_external_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        NavigationRow(
            Icons.Outlined.SupportAgent,
            stringResource(R.string.a11y_contact_support),
            stringResource(R.string.a11y_contact_support_body)
        ) { vm.navigate("Contact") }

        Notice(stringResource(R.string.contact_disclaimer))
    }

    playing?.let { id ->
        Dialog(onDismissRequest = { playing = null }) {
            Surface(shape = MaterialTheme.shapes.large) {
                VideoViewPlayer("file:///android_asset/sign_language/$id.mp4")
            }
        }
    }
}

@Composable
private fun VideoViewPlayer(path: String) {
    var view by remember { mutableStateOf<VideoView?>(null) }
    AndroidView(
        factory = { context ->
            VideoView(context).apply {
                setVideoPath(path)
                setMediaController(MediaController(context).also { it.setAnchorView(this) })
                setOnPreparedListener { start() }
                view = this
            }
        },
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
    )
    DisposableEffect(Unit) { onDispose { view?.stopPlayback() } }
}

private fun signTitle(id: String): Int = when (id) {
    "topup" -> R.string.sign_chapter_topup_title
    "pay" -> R.string.sign_chapter_pay_title
    "pass" -> R.string.sign_chapter_pass_title
    else -> R.string.sign_chapter_password_title
}

private fun signBody(id: String): Int = when (id) {
    "topup" -> R.string.sign_chapter_topup_body
    "pay" -> R.string.sign_chapter_pay_body
    "pass" -> R.string.sign_chapter_pass_body
    else -> R.string.sign_chapter_password_body
}

/** The government accessibility page exists in Chinese and English editions. */
private fun accessibilityUrl(language: AppLanguage): String =
    if (language == AppLanguage.ENGLISH) "https://www.gov.hk/en/about/accessibility/"
    else "https://www.gov.hk/tc/about/accessibility/"
