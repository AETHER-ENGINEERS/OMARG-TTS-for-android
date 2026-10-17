package com.aetherengineers.omarg.tts.neural

import android.speech.tts.Voice
import com.aetherengineers.omarg.audio.AudioBuffer
import com.aetherengineers.omarg.audio.dsp.Dsp
import com.aetherengineers.omarg.tts.EngineInfo
import com.aetherengineers.omarg.tts.SpeechEngine
import com.aetherengineers.omarg.tts.VoiceInfo
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsMatchaModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.ln

/**
 * Built-in offline neural TTS powered by sherpa-onnx (Piper/VITS, Kokoro, Kitten, Matcha, Supertonic).
 * Runs entirely on the phone's CPU; voice packs are imported from files the user downloaded.
 */
class NeuralEngine(private val installer: PackInstaller) : SpeechEngine {

    override val packageName: String = PACKAGE

    private val lock = Mutex()
    private var loaded: Pair<String, OfflineTts>? = null
    @Volatile private var stopRequested = false
    private var voiceCache: List<VoiceInfo>? = null

    /** Speaker counts are only known once a model is loaded; remember them between sessions in memory. */
    private val speakerCounts = mutableMapOf<String, Int>()

    fun packs(): List<VoicePack> = installer.installed()

    fun invalidate() { voiceCache = null }

    override fun voices(): List<VoiceInfo> = voiceCache ?: packs().flatMap { pack ->
        pack.voiceList(speakerCounts[pack.id] ?: guessSpeakers(pack)).map { v ->
            VoiceInfo(
                name = v.id, locale = v.locale,
                quality = if (pack.kind == PackKind.KOKORO || pack.kind == PackKind.SUPERTONIC || pack.id.endsWith("-high"))
                    Voice.QUALITY_VERY_HIGH else Voice.QUALITY_HIGH,
                requiresNetwork = false, installed = true,
                label = "${v.label} · ${pack.kind.label}",
            )
        }
    }.also { voiceCache = it }

    private fun guessSpeakers(pack: VoicePack): Int {
        val known = VoicePack.knownSpeakers(pack.id).size
        if (known > 0 && pack.kind != PackKind.KOKORO) return known
        // voices.bin holds one style matrix per speaker; Kokoro styles are 510×256 float32.
        if (pack.kind == PackKind.KOKORO) {
            val bytes = File(pack.dir, pack.voices).length()
            val perSpeaker = 510L * 256 * 4
            if (bytes > 0 && bytes % perSpeaker == 0L) return (bytes / perSpeaker).toInt()
            if (known > 0) return known
        }
        return 1
    }

    override fun defaultVoiceName(): String? = voices().firstOrNull()?.name

    override suspend fun synthesize(text: String, voiceName: String?, rate: Float, pitch: Float, workFile: File): AudioBuffer {
        val all = packs()
        require(all.isNotEmpty()) { "No neural voice packs installed yet. Open the Voices tab to add one." }
        val parts = voiceName?.split('#').orEmpty()
        val pack = all.firstOrNull { it.id == parts.getOrNull(0) } ?: all.first()
        val sid = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val lang = parts.getOrNull(2) ?: pack.voiceList(sid + 1).getOrNull(sid)?.lang

        stopRequested = false
        val audio = lock.withLock {
            withContext(Dispatchers.Default) {
                val tts = load(pack)
                val config = GenerationConfig(sid = sid.coerceIn(0, (tts.numSpeakers() - 1).coerceAtLeast(0)), speed = rate.coerceIn(0.3f, 3f))
                if (lang != null) config.extra = mapOf("lang" to lang)
                val ctx = currentCoroutineContext()
                tts.generateWithConfigAndCallback(text, config, StopCallback { stopRequested || !ctx.isActive })
            }
        }
        if (stopRequested) throw kotlinx.coroutines.CancellationException("Stopped")
        require(audio.samples.isNotEmpty()) { "This voice could not pronounce: \"${text.take(40)}…\"" }
        var samples = audio.samples
        if (abs(pitch - 1f) > 0.01f) {
            val semitones = 12.0 * ln(pitch.toDouble()) / ln(2.0)
            samples = withContext(Dispatchers.Default) { Dsp.pitchShift(samples, audio.sampleRate, semitones) }
        }
        return AudioBuffer(samples, audio.sampleRate)
    }

    /** Keeps exactly one model in memory — Kokoro alone needs a few hundred MB. */
    private fun load(pack: VoicePack): OfflineTts {
        loaded?.let { (id, tts) -> if (id == pack.id) return tts }
        loaded?.second?.release()
        loaded = null
        val tts = OfflineTts(config = configFor(pack))
        loaded = pack.id to tts
        if (speakerCounts[pack.id] != tts.numSpeakers()) {
            speakerCounts[pack.id] = tts.numSpeakers()
            voiceCache = null
        }
        return tts
    }

    private fun configFor(pack: VoicePack): OfflineTtsConfig {
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        val p = pack::path
        val lexicon = pack.lexicons.joinToString(",") { p(it) }
        val model = when (pack.kind) {
            PackKind.VITS -> OfflineTtsModelConfig(vits = OfflineTtsVitsModelConfig(
                model = p(pack.model), lexicon = lexicon, tokens = p(pack.tokens), dataDir = p(pack.dataDir)))
            PackKind.MATCHA -> OfflineTtsModelConfig(matcha = OfflineTtsMatchaModelConfig(
                acousticModel = p(pack.model), vocoder = p(pack.vocoder), lexicon = lexicon, tokens = p(pack.tokens), dataDir = p(pack.dataDir)))
            PackKind.KOKORO -> OfflineTtsModelConfig(kokoro = OfflineTtsKokoroModelConfig(
                model = p(pack.model), voices = p(pack.voices), tokens = p(pack.tokens), dataDir = p(pack.dataDir), lexicon = lexicon))
            PackKind.KITTEN -> OfflineTtsModelConfig(kitten = OfflineTtsKittenModelConfig(
                model = p(pack.model), voices = p(pack.voices), tokens = p(pack.tokens), dataDir = p(pack.dataDir)))
            PackKind.SUPERTONIC -> {
                val f = pack.extraFiles
                OfflineTtsModelConfig(supertonic = OfflineTtsSupertonicModelConfig(
                    durationPredictor = p(f.getValue("durationPredictor")), textEncoder = p(f.getValue("textEncoder")),
                    vectorEstimator = p(f.getValue("vectorEstimator")), vocoder = p(f.getValue("vocoder")),
                    ttsJson = p(f.getValue("ttsJson")), unicodeIndexer = p(f.getValue("unicodeIndexer")),
                    voiceStyle = p(f.getValue("voiceStyle"))))
            }
        }
        model.numThreads = threads
        model.provider = "cpu"
        return OfflineTtsConfig(
            model = model,
            ruleFsts = pack.ruleFsts.joinToString(",") { p(it) },
            ruleFars = pack.ruleFars.joinToString(",") { p(it) },
        )
    }

    private fun unload() {
        loaded?.second?.release()
        loaded = null
    }

    /** Frees the loaded model once any synthesis in progress has finished (e.g. before deleting its pack). */
    suspend fun release() = lock.withLock { unload() }

    override fun stop() { stopRequested = true }

    override fun shutdown() { stop(); unload() }

    companion object {
        const val PACKAGE = "omarg.neural"
        val INFO = EngineInfo(PACKAGE, "OMARG Neural (built-in, offline)")
    }
}
