package com.aetherengineers.omarg.render

import android.content.Context
import android.net.Uri
import com.aetherengineers.omarg.audio.AudioBuffer
import com.aetherengineers.omarg.audio.AudioDecoder
import com.aetherengineers.omarg.audio.Mixer
import com.aetherengineers.omarg.audio.dsp.Dsp
import com.aetherengineers.omarg.audio.dsp.EffectChain
import com.aetherengineers.omarg.data.MusicBed
import com.aetherengineers.omarg.data.VoiceSlot
import com.aetherengineers.omarg.tts.Script
import com.aetherengineers.omarg.tts.ScriptPart
import com.aetherengineers.omarg.tts.SpeechEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

data class RenderRequest(
    val text: String,
    val slots: List<VoiceSlot>,
    val masterEffect: EffectChain?,
    val slotEffects: Map<Int, EffectChain>,
    val music: MusicBed?,
    val trimSilence: Boolean,
    val normalize: Boolean,
    val sentenceGapMs: Int,
)

/** Text → speech → per-voice effects → master effects → music bed → limiter. Runs fully offline. */
class Renderer(private val context: Context, private val engineFor: suspend (String?) -> SpeechEngine?) {

    suspend fun render(req: RenderRequest, progress: (String, Float) -> Unit): AudioBuffer {
        val work = File(context.cacheDir, "render").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val parts = Script.parse(req.text)
        val speechCount = parts.count { it is ScriptPart.Speech }.coerceAtLeast(1)
        require(parts.any { it is ScriptPart.Speech }) { "Nothing to say — type some text first." }

        val pieces = mutableListOf<AudioBuffer>()
        var sampleRate = 0
        var spoken = 0
        var previousWasSpeech = false
        for (part in parts) {
            coroutineContext.ensureActive()
            when (part) {
                is ScriptPart.Pause -> {
                    pieces += AudioBuffer.silence(part.seconds, sampleRate.takeIf { it > 0 } ?: 24000)
                    previousWasSpeech = false
                }
                is ScriptPart.Speech -> {
                    progress("Speaking ${spoken + 1} of $speechCount", 0.6f * spoken / speechCount)
                    val slot = req.slots.getOrElse(part.voiceSlot) { req.slots.first() }
                    val engine = engineFor(slot.engine) ?: error("Could not start the text-to-speech engine")
                    var audio = engine.synthesize(part.text, slot.voice, slot.rate, slot.pitch, File(work, "part_$spoken.wav"))
                    if (sampleRate == 0) sampleRate = audio.sampleRate
                    if (req.trimSilence) audio = AudioBuffer(Dsp.trimSilence(audio.samples, audio.sampleRate), audio.sampleRate)
                    req.slotEffects[part.voiceSlot]?.let { fx -> audio = withContext(Dispatchers.Default) { fx.process(audio) } }
                    if (previousWasSpeech && req.sentenceGapMs > 0) pieces += AudioBuffer.silence(req.sentenceGapMs / 1000.0, audio.sampleRate)
                    pieces += audio
                    previousWasSpeech = true
                    spoken++
                }
            }
        }

        return withContext(Dispatchers.Default) {
            var out = AudioBuffer.concat(pieces, sampleRate.takeIf { it > 0 } ?: 24000)
            req.masterEffect?.takeIf { it.steps.isNotEmpty() }?.let { fx ->
                progress("Applying ${fx.name}", 0.65f)
                out = fx.process(out) { progress("Applying ${fx.name}", 0.65f + 0.25f * it) }
            }
            if (req.normalize) out = AudioBuffer(Dsp.normalize(out.samples, -1.5), out.sampleRate)
            req.music?.let { bed ->
                progress("Mixing music", 0.92f)
                val music = AudioDecoder.decode(context, Uri.parse(bed.uri))
                out = Mixer.mixBackground(out, music, bed.volume, bed.duck, bed.loop)
            }
            progress("Finishing", 0.98f)
            AudioBuffer(Dsp.limit(out.samples), out.sampleRate)
        }
    }
}
