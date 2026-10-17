package com.aetherengineers.onarg.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

data class EngineInfo(val packageName: String, val label: String)

data class VoiceInfo(
    val name: String,
    val locale: Locale,
    val quality: Int,
    val requiresNetwork: Boolean,
    val installed: Boolean,
) {
    val languageLabel: String get() = locale.getDisplayName(Locale.getDefault())
    val qualityLabel: String
        get() = when {
            quality >= Voice.QUALITY_VERY_HIGH -> "Very high"
            quality >= Voice.QUALITY_HIGH -> "High"
            quality >= Voice.QUALITY_NORMAL -> "Normal"
            else -> "Low"
        }
}

/** Coroutine wrapper around one Android [TextToSpeech] engine (Samsung, Google, SherpaTTS, RHVoice, …). */
class TtsEngine private constructor(private val tts: TextToSpeech, val packageName: String) {

    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {}
            override fun onDone(utteranceId: String) { pending.remove(utteranceId)?.complete(true) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) { pending.remove(utteranceId)?.complete(false) }
            override fun onError(utteranceId: String, errorCode: Int) { pending.remove(utteranceId)?.complete(false) }
            override fun onStop(utteranceId: String, interrupted: Boolean) { pending.remove(utteranceId)?.complete(false) }
        })
    }

    val engines: List<EngineInfo> get() = tts.engines.map { EngineInfo(it.name, it.label) }

    val maxInputLength: Int get() = TextToSpeech.getMaxSpeechInputLength()

    fun voices(): List<VoiceInfo> = runCatching { tts.voices }.getOrNull().orEmpty().map {
        VoiceInfo(
            name = it.name,
            locale = it.locale,
            quality = it.quality,
            requiresNetwork = it.isNetworkConnectionRequired,
            installed = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty(),
        )
    }.sortedWith(compareBy({ it.languageLabel }, { -it.quality }, { it.name }))

    fun defaultVoiceName(): String? = runCatching { tts.defaultVoice?.name ?: tts.voice?.name }.getOrNull()

    /** Synthesizes [text] to a WAV [file]. Returns false if the engine reported an error. */
    suspend fun synthesize(text: String, voiceName: String?, rate: Float, pitch: Float, file: File): Boolean {
        voiceName?.let { name -> tts.voices?.firstOrNull { it.name == name }?.let { tts.voice = it } }
        tts.setSpeechRate(rate)
        tts.setPitch(pitch)
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Boolean>()
        pending[id] = done
        val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id) }
        if (tts.synthesizeToFile(text, params, file, id) != TextToSpeech.SUCCESS) {
            pending.remove(id)
            return false
        }
        // Generous timeout: long chunks on slow neural engines can take a while.
        return withTimeout(180_000L + text.length * 200L) { done.await() } && file.length() > 44
    }

    fun stop() { tts.stop() }

    fun shutdown() {
        pending.values.forEach { it.complete(false) }
        pending.clear()
        tts.shutdown()
    }

    companion object {
        /** Creates and initialises an engine; `null` package means the system default engine. */
        suspend fun create(context: Context, enginePackage: String?): TtsEngine? {
            var instance: TextToSpeech? = null
            val ok = suspendCancellableCoroutine { cont ->
                instance = TextToSpeech(context.applicationContext, { status ->
                    if (cont.isActive) cont.resume(status == TextToSpeech.SUCCESS)
                }, enginePackage)
            }
            val tts = instance ?: return null
            if (!ok) { tts.shutdown(); return null }
            return TtsEngine(tts, enginePackage ?: tts.defaultEngine)
        }
    }
}
