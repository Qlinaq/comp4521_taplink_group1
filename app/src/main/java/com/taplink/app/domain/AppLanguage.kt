package com.taplink.app.domain

import java.util.Locale

/**
 * Interface languages. The resource tag matches the Android resource folder so
 * `values-zh-rTW` and `values-zh-rCN` are selected deterministically instead of relying on
 * language-only fallbacks.
 *
 * [nativeName] is intentionally not translated: a language picker has to be readable to someone
 * who does not yet understand the current interface language.
 */
enum class AppLanguage(val resourceTag: String, val nativeName: String) {
    ENGLISH("en", "English"),
    TRADITIONAL_CHINESE("zh-TW", "繁體中文"),
    SIMPLIFIED_CHINESE("zh-CN", "简体中文");

    val locale: Locale get() = Locale.forLanguageTag(resourceTag)

    companion object {
        fun fromStorage(value: String?): AppLanguage =
            entries.firstOrNull { it.name == value } ?: ENGLISH
    }
}
