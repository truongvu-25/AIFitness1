package com.google.mediapipe.examples.poselandmarker.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * Process-wide Vietnamese TTS engine.
 *
 * The application preloads this manager at startup so workout guidance does not
 * have to wait for a new TTS service connection every time CameraFragment opens.
 */
object VoiceCoachManager {
    const val VOICE_OFF = "off"
    const val VOICE_FEMALE = "female"
    const val VOICE_MALE = "male"
    const val VOICE_DEFAULT = "default"
    const val NORMAL_SPEECH_RATE = 0.92f
    const val CALIBRATION_SPEECH_RATE = 0.82f

    private const val TAG = "VoiceCoachManager"
    private const val PREFERENCES_NAME = "tri_force_voice_coach"
    private const val KEY_VOICE_MODE = "voice_mode"

    private var textToSpeech: TextToSpeech? = null
    private var applicationContext: Context? = null
    private var ready = false
    private var initializing = false
    private var speaking = false
    private var activeUtteranceId: String? = null
    private var voiceMode = VOICE_FEMALE
    private var pendingSpeech: PendingSpeech? = null
    private val readyCallbacks = mutableListOf<(Boolean) -> Unit>()

    @Synchronized
    fun initialize(context: Context, onReady: ((Boolean) -> Unit)? = null) {
        applicationContext = context.applicationContext
        voiceMode = preferences(context).getString(KEY_VOICE_MODE, VOICE_FEMALE)
            ?: VOICE_FEMALE

        if (ready) {
            onReady?.invoke(true)
            return
        }
        onReady?.let(readyCallbacks::add)
        if (initializing) return

        initializing = true
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            val engine = textToSpeech
            val initializedSuccessfully = if (status == TextToSpeech.SUCCESS && engine != null) {
                val languageResult = engine.setLanguage(Locale.forLanguageTag("vi-VN"))
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        synchronized(this@VoiceCoachManager) {
                            if (activeUtteranceId == utteranceId) speaking = true
                        }
                    }

                    override fun onDone(utteranceId: String?) = finishUtterance(utteranceId)

                    @Deprecated("Deprecated by the Android TTS API")
                    override fun onError(utteranceId: String?) = finishUtterance(utteranceId)

                    override fun onStop(utteranceId: String?, interrupted: Boolean) =
                        finishUtterance(utteranceId)
                })
                languageResult != TextToSpeech.LANG_MISSING_DATA &&
                    languageResult != TextToSpeech.LANG_NOT_SUPPORTED
            } else {
                false
            }

            val callbacks: List<(Boolean) -> Unit>
            val queuedSpeech: PendingSpeech?
            synchronized(this) {
                ready = initializedSuccessfully
                initializing = false
                if (ready) {
                    applyVoicePreferenceLocked()
                }
                callbacks = readyCallbacks.toList()
                readyCallbacks.clear()
                queuedSpeech = if (ready) pendingSpeech else null
                pendingSpeech = null
            }

            if (!initializedSuccessfully) {
                Log.w(TAG, "Không thể khởi tạo giọng đọc tiếng Việt")
            }
            callbacks.forEach { it(initializedSuccessfully) }
            queuedSpeech?.let(::speakNow)
        }
    }

    @Synchronized
    fun currentMode(context: Context): String {
        if (applicationContext == null) applicationContext = context.applicationContext
        voiceMode = preferences(context).getString(KEY_VOICE_MODE, VOICE_FEMALE)
            ?: VOICE_FEMALE
        return voiceMode
    }

    @Synchronized
    fun setMode(context: Context, mode: String) {
        applicationContext = context.applicationContext
        pendingSpeech = null
        textToSpeech?.stop()
        speaking = false
        activeUtteranceId = null
        voiceMode = mode
        preferences(context).edit().putString(KEY_VOICE_MODE, mode).apply()
        if (ready) applyVoicePreferenceLocked()
    }

    @Synchronized
    fun speak(
        text: String,
        utteranceId: String,
        speechRate: Float = NORMAL_SPEECH_RATE
    ) {
        if (text.isBlank() || voiceMode == VOICE_OFF) return
        val speech = PendingSpeech(text, utteranceId, speechRate.coerceIn(0.75f, 1.05f))
        if (!ready || speaking) {
            // Keep only the newest useful instruction. Repeatedly interrupting TTS
            // produces clipped/static audio when camera feedback changes quickly.
            pendingSpeech = speech
            if (!ready) applicationContext?.let { initialize(it) }
            return
        }
        speakNow(speech)
    }

    @Synchronized
    fun stop() {
        pendingSpeech = null
        speaking = false
        activeUtteranceId = null
        textToSpeech?.stop()
    }

    private fun applyVoicePreferenceLocked() {
        val engine = textToSpeech ?: return
        val vietnameseVoices = engine.voices
            ?.filter { it.locale.language == "vi" }
            .orEmpty()
        val defaultVietnameseVoice = engine.defaultVoice
            ?.takeIf { it.locale.language == "vi" }
        val preferredVoice = when (voiceMode) {
            VOICE_FEMALE, VOICE_MALE -> vietnameseVoices
                .sortedWith(
                    compareByDescending<Voice> { genderMatchScore(it, voiceMode) }
                        .thenBy { it.isNetworkConnectionRequired }
                        .thenByDescending { it.quality }
                        .thenBy { it.latency }
                )
                .firstOrNull { genderMatchScore(it, voiceMode) > 0 }
                ?: defaultVietnameseVoice
                ?: vietnameseVoices.firstOrNull { !it.isNetworkConnectionRequired }
                ?: vietnameseVoices.firstOrNull()
            else -> defaultVietnameseVoice
                ?: vietnameseVoices.firstOrNull { !it.isNetworkConnectionRequired }
                ?: vietnameseVoices.firstOrNull()
        }
        preferredVoice?.let { engine.voice = it }

        // Use the engine's real voice instead of pitch-shifting it. Pitch shifting
        // made Vietnamese speech metallic and could confuse the perceived gender.
        engine.setPitch(1.0f)
        engine.setSpeechRate(NORMAL_SPEECH_RATE)
    }

    private fun genderMatchScore(voice: Voice, mode: String): Int {
        val tokens = voice.name
            .lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9]+"))
            .filter(String::isNotBlank)
            .toSet()
        return when (mode) {
            VOICE_FEMALE -> when {
                "female" in tokens -> 100
                "vif" in tokens -> 90
                "woman" in tokens -> 80
                else -> 0
            }
            VOICE_MALE -> when {
                // Exact token matching prevents "male" from matching "female".
                "male" in tokens -> 100
                "vim" in tokens -> 90
                "man" in tokens -> 80
                else -> 0
            }
            else -> 0
        }
    }

    @Synchronized
    private fun speakNow(speech: PendingSpeech) {
        val engine = textToSpeech ?: return
        if (voiceMode == VOICE_OFF) return
        engine.setSpeechRate(speech.speechRate)
        speaking = true
        activeUtteranceId = speech.utteranceId
        val parameters = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.90f)
        }
        val result = engine.speak(
            speech.text,
            TextToSpeech.QUEUE_FLUSH,
            parameters,
            speech.utteranceId
        )
        if (result == TextToSpeech.ERROR) {
            speaking = false
            activeUtteranceId = null
        }
    }

    private fun finishUtterance(utteranceId: String?) {
        val nextSpeech = synchronized(this) {
            if (utteranceId != activeUtteranceId) return
            speaking = false
            activeUtteranceId = null
            pendingSpeech.also { pendingSpeech = null }
        }
        nextSpeech?.let(::speakNow)
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private data class PendingSpeech(
        val text: String,
        val utteranceId: String,
        val speechRate: Float
    )
}
