package com.taplink.app.platform

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.taplink.app.domain.AppLanguage
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads screen summaries aloud for users who cannot comfortably read small text.
 *
 * This is the platform speech engine, not a recording: it needs a voice installed on the device.
 * Two failure modes used to be indistinguishable from success — no engine at all (the manifest was
 * missing the `TTS_SERVICE` query, so Android 11+ refused to bind one) and a missing voice for the
 * interface language. [speak] therefore reports whether the utterance will actually be heard, and
 * the caller shows a message instead of leaving the user in silence wondering what went wrong.
 */
class VoiceReader(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private var engine: TextToSpeech? = null
    private val ready = AtomicBoolean(false)
    private var pending: Pair<String, Locale>? = null
    @Volatile var isAvailable: Boolean = false
        private set
    @Volatile var lastError: Boolean = false

    init {
        start()
    }

    /** Creates the engine. Called again by [speak] if the first attempt failed, so one bad start
     *  (for example the service still being installed) does not silence the whole session. */
    private fun start() {
        lastError = false
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
                // Speech is a media stream: this keeps it audible on the volume the user actually
                // adjusts, and marks it as accessibility output so other apps duck around it.
                runCatching {
                    tts.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                }
                ready.set(true)
                isAvailable = tts.engines.isNotEmpty()
                pending?.let { (text, locale) -> speakNow(text, locale) }
                pending = null
            } else {
                ready.set(false)
                isAvailable = false
                lastError = true
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

    /**
     * @return true when this text will be (or already was) spoken, false when the device cannot
     * speak it at all — no engine bound, or no voice for the language and its fallback.
     */
    fun speak(text: String, language: AppLanguage): Boolean {
        val clean = text.trim().take(MAX_CHARS)
        if (clean.isEmpty()) return true
        if (!ready.get()) {
            // The very first tap can arrive while the engine is still binding; queue it and retry
            // the binding so a previous failure does not make every later tap silent.
            pending = clean to localeFor(language)
            if (engine == null) start()
            return true
        }
        return speakNow(clean, localeFor(language))
    }

    private fun speakNow(text: String, locale: Locale): Boolean {
        val tts = engine ?: return false
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
        if (!isAvailable) { lastError = true; return false }
        tts.setSpeechRate(0.92f)
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID) == TextToSpeech.SUCCESS
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
