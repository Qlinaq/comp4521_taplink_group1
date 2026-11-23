package com.taplink.app.platform

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.taplink.app.domain.AppLanguage
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads screen summaries aloud for users who cannot comfortably read small text.
 *
 * This is the platform speech engine, not a recording: it needs a Chinese voice installed on the
 * device. When no matching voice exists the reader reports [isAvailable] false and the UI says so
 * instead of silently doing nothing.
 */
class VoiceReader(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private var engine: TextToSpeech? = null
    private val ready = AtomicBoolean(false)
    private var pending: Pair<String, Locale>? = null
    @Volatile var isAvailable: Boolean = false
        private set
    @Volatile var lastError: Boolean = false
        private set

    init {
        engine = TextToSpeech(appContext) { status ->
            val tts = engine
            if (status == TextToSpeech.SUCCESS && tts != null) {
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) = Unit
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { lastError = true }
                    override fun onError(utteranceId: String?, errorCode: Int) { lastError = true }
                })
                ready.set(true)
                pending?.let { (text, locale) -> speakNow(text, locale) }
                pending = null
            } else {
                isAvailable = false
            }
        }
    }

    /** Hong Kong Traditional Chinese is preferred for zh-TW; the engine may only ship zh-CN. */
    private fun localeFor(language: AppLanguage): Locale = when (language) {
        AppLanguage.TRADITIONAL_CHINESE -> Locale("zh", "HK")
        AppLanguage.SIMPLIFIED_CHINESE -> Locale("zh", "CN")
        AppLanguage.ENGLISH -> Locale("en", "HK")
    }

    private fun fallbackLocale(language: AppLanguage): Locale = when (language) {
        AppLanguage.TRADITIONAL_CHINESE -> Locale("zh", "TW")
        AppLanguage.SIMPLIFIED_CHINESE -> Locale("zh", "CN")
        AppLanguage.ENGLISH -> Locale.UK
    }

    fun speak(text: String, language: AppLanguage) {
        val clean = text.trim().take(MAX_CHARS)
        if (clean.isEmpty()) return
        if (!ready.get()) { pending = clean to localeFor(language); return }
        speakNow(clean, localeFor(language))
    }

    private fun speakNow(text: String, locale: Locale) {
        val tts = engine ?: return
        lastError = false
        val result = tts.setLanguage(locale)
        val usable = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        if (!usable) {
            // A missing zh-HK voice is common; zh-TW or the device default still helps.
            val retry = tts.setLanguage(fallbackLocaleFor(locale))
            isAvailable = retry != TextToSpeech.LANG_MISSING_DATA && retry != TextToSpeech.LANG_NOT_SUPPORTED
        } else {
            isAvailable = true
        }
        if (!isAvailable) { lastError = true; return }
        tts.setSpeechRate(0.92f)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    private fun fallbackLocaleFor(locale: Locale): Locale = when (locale.language) {
        "zh" -> Locale("zh", "TW")
        "en" -> Locale.UK
        else -> Locale.getDefault()
    }

    fun stop() { engine?.stop() }

    override fun close() {
        pending = null
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready.set(false)
    }

    private companion object {
        const val UTTERANCE_ID = "taplink_summary"
        const val MAX_CHARS = 600
    }
}
