package com.taplink.app.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.taplink.app.domain.AppLanguage
import java.util.Locale

/**
 * The locale currently on screen. Date helpers read this instead of [Locale.getDefault], because a
 * plain Java locale change is not observable and Compose would keep already-composed items (the
 * month header, transaction dates) in the previous language.
 */
val LocalAppLocale = staticCompositionLocalOf { Locale.getDefault() }

/**
 * Applies the chosen interface language to everything below it.
 *
 * A configuration-scoped context is provided instead of recreating the activity, so switching
 * language is instant and does not lose navigation state. `minSdk 29` rules out the API 33
 * `LocaleManager` and the project does not depend on AppCompat, so this is the portable approach.
 *
 * The process default locale is updated as well, so that any framework call that resolves it
 * directly (number and date formatting outside Compose) agrees with the screen.
 */
@Composable
fun LocalizedContent(language: AppLanguage, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val localized = remember(language, configuration) {
        Locale.setDefault(Locale.Category.DISPLAY, language.locale)
        Locale.setDefault(Locale.Category.FORMAT, language.locale)
        Configuration(configuration).apply { setLocale(language.locale) }
            .let { context.createConfigurationContext(it) }
    }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
        LocalAppLocale provides language.locale,
        content = content
    )
}
