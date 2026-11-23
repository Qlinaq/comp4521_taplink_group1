package com.taplink.app.platform

import android.content.Context
import android.view.inputmethod.InputMethodManager
import com.taplink.app.domain.AppLanguage
import java.util.Locale

/**
 * Whether the enabled on-screen keyboards can type the interface language.
 *
 * A device can be set to Chinese while every installed keyboard only offers a Latin layout — the
 * common case on an emulator, where a host input method never reaches the guest. The app cannot
 * install a keyboard, but it can notice the gap and send the user to the right system screen
 * instead of leaving them typing into a field that silently cannot accept Chinese.
 */
object KeyboardSupport {
    fun canType(context: Context, language: AppLanguage): Boolean {
        val tag = when (language) {
            AppLanguage.ENGLISH -> "en"
            AppLanguage.TRADITIONAL_CHINESE, AppLanguage.SIMPLIFIED_CHINESE -> "zh"
        }
        val manager = context.getSystemService(InputMethodManager::class.java) ?: return true
        return manager.enabledInputMethodList.any { ime ->
            // A subtype list can be empty on some vendors' keyboards; treat that as "unknown" so the
            // app never nags a user whose keyboard does work.
            if (ime.subtypeCount == 0) return@any false
            (0 until ime.subtypeCount).any { index ->
                // getLocale() is deprecated; the BCP-47 tag is the supported source. Only the
                // primary language subtag is compared, because the script subtag (Hans/Hant) and
                // the region must not disqualify a keyboard: either Chinese layout can type either
                // Chinese interface, and a Hong Kong English layout is still English.
                val languageCode = Locale.forLanguageTag(
                    ime.getSubtypeAt(index).languageTag.orEmpty().replace('_', '-')
                ).language
                languageCode.equals(tag, ignoreCase = true)
            }
        }
    }
}
