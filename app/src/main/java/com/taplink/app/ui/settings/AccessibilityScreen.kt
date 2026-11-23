package com.taplink.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taplink.app.R
import com.taplink.app.ui.*

/**
 * Accessibility and elderly-friendly settings.
 *
 * Each option is independent so a user keeps control: the master switch turns the four on together
 * and turns them off together, which is what a two-state switch has to mean to be predictable. A
 * user who wants large text but not read-aloud turns the master on, then turns read-aloud off; the
 * individual switches never touch each other.
 */
@Composable
fun AccessibilityScreen(vm: WalletViewModel, platform: PlatformActions) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    ScreenColumn {
        PageHeading(
            stringResource(R.string.a11y_eyebrow),
            stringResource(R.string.a11y_title),
            stringResource(R.string.a11y_subtitle)
        )
        VoiceSummary(
            listOf(
                stringResource(R.string.a11y_title),
                stringResource(R.string.a11y_state_large_text, yesNo(prefs.largeText)),
                stringResource(R.string.a11y_state_contrast, yesNo(prefs.highContrast)),
                stringResource(R.string.a11y_state_voice, yesNo(prefs.voiceReading)),
                stringResource(R.string.a11y_state_simple, yesNo(prefs.elderlyMode))
            ).joinToString("。")
        )

        Panel {
            PreferenceSwitch(
                stringResource(R.string.a11y_elderly_title),
                stringResource(R.string.a11y_elderly_body),
                prefs.elderlyMode,
                !busy
            ) { checked ->
                vm.updatePreferences {
                    if (checked) it.copy(elderlyMode = true, largeText = true, highContrast = true, voiceReading = true)
                    else it.copy(elderlyMode = false, largeText = false, highContrast = false, voiceReading = false)
                }
            }
            if (prefs.elderlyMode) ModeBanner(stringResource(R.string.a11y_mode_active))
        }

        Panel {
            SectionHeading(stringResource(R.string.a11y_section_display))
            PreferenceSwitch(
                stringResource(R.string.a11y_large_text),
                stringResource(R.string.a11y_large_text_body),
                prefs.largeText,
                !busy
            ) { checked -> vm.updatePreferences { it.copy(largeText = checked) } }
            PreferenceSwitch(
                stringResource(R.string.a11y_high_contrast),
                stringResource(R.string.a11y_high_contrast_body),
                prefs.highContrast,
                !busy
            ) { checked -> vm.updatePreferences { it.copy(highContrast = checked) } }
            HelperText(stringResource(R.string.a11y_display_note))
        }

        Panel {
            SectionHeading(stringResource(R.string.a11y_section_voice))
            PreferenceSwitch(
                stringResource(R.string.a11y_voice),
                stringResource(R.string.a11y_voice_body),
                prefs.voiceReading,
                !busy
            ) { checked -> vm.updatePreferences { it.copy(voiceReading = checked) } }
            platform.speak?.let { speak ->
                val sample = stringResource(R.string.a11y_voice_sample)
                OutlinedButton(onClick = { speak(sample) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.VolumeUp, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.a11y_voice_test))
                }
            }
            HelperText(stringResource(R.string.a11y_voice_note))
        }

        Panel {
            SectionHeading(stringResource(R.string.a11y_simple_flow))
            Text(stringResource(R.string.a11y_simple_flow_body), style = MaterialTheme.typography.bodyMedium)
            HelperText(stringResource(R.string.a11y_simple_flow_note))
        }

        Panel {
            SectionHeading(stringResource(R.string.a11y_section_standard))
            Text(stringResource(R.string.a11y_standard_body), style = MaterialTheme.typography.bodyMedium)
            platform.openAccessibilitySettings?.let { open ->
                OutlinedButton(open, Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Accessibility, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.a11y_open_system))
                }
            }
        }

        NavigationRow(
            Icons.Outlined.SignLanguage,
            stringResource(R.string.a11y_sign_language),
            stringResource(R.string.a11y_sign_language_body)
        ) { vm.navigate("SignLanguage") }
        NavigationRow(
            Icons.Outlined.SupportAgent,
            stringResource(R.string.a11y_contact_support),
            stringResource(R.string.a11y_contact_support_body)
        ) { vm.navigate("Contact") }
    }
}

@Composable
private fun yesNo(value: Boolean): String = stringResource(if (value) R.string.a11y_state_on else R.string.a11y_state_off)
