package com.aetherengineers.omarg.tts.neural

import java.io.File
import java.util.Locale

enum class PackKind(val label: String) { VITS("Piper / VITS"), MATCHA("Matcha"), KOKORO("Kokoro"), KITTEN("Kitten"), SUPERTONIC("Supertonic") }

/** One selectable voice inside a pack: a speaker id, optionally with a language hint for multilingual models. */
data class PackVoice(val id: String, val label: String, val locale: Locale, val speakerId: Int, val lang: String?)

/**
 * A neural voice model unpacked on the device. Paths are relative to [dir] so packs survive app-data moves.
 * Layouts follow the sherpa-onnx `tts-models` release archives.
 */
data class VoicePack(
    val dir: File,
    val kind: PackKind,
    val model: String = "",
    val tokens: String = "",
    val voices: String = "",
    val vocoder: String = "",
    val lexicons: List<String> = emptyList(),
    val dataDir: String = "",
    val ruleFsts: List<String> = emptyList(),
    val ruleFars: List<String> = emptyList(),
    /** Supertonic-only files, by role. */
    val extraFiles: Map<String, String> = emptyMap(),
) {
    val id: String get() = dir.name

    fun path(rel: String) = if (rel.isEmpty()) "" else File(dir, rel).absolutePath

    val displayName: String get() = describe(id)

    /** Lists voices given the number of speakers the loaded model reports. */
    fun voiceList(numSpeakers: Int): List<PackVoice> {
        val n = numSpeakers.coerceAtLeast(1)
        val names = knownSpeakers(id)
        return when (kind) {
            PackKind.SUPERTONIC -> SUPERTONIC_LANGS.flatMap { lang ->
                (0 until n).map { sid ->
                    PackVoice("$id#$sid#$lang", "Speaker ${sid + 1}", Locale.forLanguageTag(lang), sid, lang)
                }
            }
            PackKind.KOKORO -> (0 until n).map { sid ->
                val name = names.getOrNull(sid)
                val (locale, lang) = name?.let { kokoroLanguage(it) } ?: (packLocale(id) to null)
                PackVoice("$id#$sid", name?.let(::prettySpeaker) ?: "Speaker ${sid + 1}", locale, sid, lang)
            }
            else -> (0 until n).map { sid ->
                val name = names.getOrNull(sid)
                val label = when {
                    name != null -> prettySpeaker(name)
                    n == 1 -> describeVoice(id)
                    else -> "${describeVoice(id)} · speaker ${sid + 1}"
                }
                PackVoice("$id#$sid", label, packLocale(id), sid, null)
            }
        }
    }

    companion object {
        val SUPERTONIC_LANGS = listOf(
            "en", "ko", "ja", "ar", "bg", "cs", "da", "de", "el", "es", "et", "fi", "fr", "hi", "hr", "hu",
            "id", "it", "lt", "lv", "nl", "pl", "pt", "ro", "ru", "sk", "sl", "sv", "tr", "uk", "vi",
        )

        private val KOKORO_V019 = "af,af_bella,af_nicole,af_sarah,af_sky,am_adam,am_michael,bf_emma,bf_isabella,bm_george,bm_lewis".split(',')
        private val KOKORO_V10 = ("af_alloy,af_aoede,af_bella,af_heart,af_jessica,af_kore,af_nicole,af_nova,af_river,af_sarah,af_sky," +
            "am_adam,am_echo,am_eric,am_fenrir,am_liam,am_michael,am_onyx,am_puck,am_santa,bf_alice,bf_emma,bf_isabella,bf_lily," +
            "bm_daniel,bm_fable,bm_george,bm_lewis,ef_dora,em_alex,ff_siwis,hf_alpha,hf_beta,hm_omega,hm_psi,if_sara,im_nicola," +
            "jf_alpha,jf_gongitsune,jf_nezumi,jf_tebukuro,jm_kumo,pf_dora,pm_alex,pm_santa,zf_xiaobei,zf_xiaoni,zf_xiaoxiao," +
            "zf_xiaoyi,zm_yunjian,zm_yunxi,zm_yunxia,zm_yunyang,em_santa").split(',')
        private val KOKORO_V11_PREFIX = listOf("af_maple", "af_sol", "bf_vale")
        private val KITTEN = listOf("expr-voice-2-m", "expr-voice-2-f", "expr-voice-3-m", "expr-voice-3-f",
            "expr-voice-4-m", "expr-voice-4-f", "expr-voice-5-m", "expr-voice-5-f")

        fun knownSpeakers(id: String): List<String> = when {
            id.startsWith("kokoro-en-v0_19") || id.startsWith("kokoro-int8-en-v0_19") -> KOKORO_V019
            id.contains("kokoro") && id.contains("v1_0") -> KOKORO_V10
            id.contains("kokoro") && id.contains("v1_1") -> KOKORO_V11_PREFIX
            id.contains("kitten") -> KITTEN
            else -> emptyList()
        }

        /** "af_bella" → "Bella (female)", "expr-voice-3-m" → "Voice 3 (male)". */
        fun prettySpeaker(name: String): String {
            if (name.startsWith("expr-voice-")) {
                val parts = name.removePrefix("expr-voice-").split('-')
                return "Voice ${parts[0]} (${if (parts.getOrNull(1) == "f") "female" else "male"})"
            }
            val prefix = name.substringBefore('_', "")
            val base = name.substringAfter('_', name).replaceFirstChar { it.titlecase() }
            val gender = when (prefix.getOrNull(1)) { 'f' -> " (female)"; 'm' -> " (male)"; else -> "" }
            return if (prefix.isEmpty()) "Default" else base + gender
        }

        /** Kokoro voice prefixes encode the language: a=US English, b=UK English, e=Spanish, … */
        fun kokoroLanguage(name: String): Pair<Locale, String?> = when (name.firstOrNull()) {
            'a' -> Locale.US to null
            'b' -> Locale.UK to null
            'e' -> Locale.forLanguageTag("es-ES") to "es"
            'f' -> Locale.FRANCE to "fr"
            'h' -> Locale.forLanguageTag("hi-IN") to "hi"
            'i' -> Locale.ITALY to "it"
            'j' -> Locale.JAPAN to "ja"
            'p' -> Locale.forLanguageTag("pt-BR") to "pt-br"
            'z' -> Locale.CHINA to null
            else -> Locale.US to null
        }

        private val localeInName = Regex("""(?:^|[-_])([a-z]{2})_([A-Z]{2})(?:[-_]|$)""")

        fun packLocale(id: String): Locale {
            localeInName.find(id)?.let { return Locale(it.groupValues[1], it.groupValues[2]) }
            return when {
                id.contains("kitten") || id.contains("kokoro-en") || id.contains("-en-") || id.contains("ljs") || id.contains("vctk") -> Locale.US
                id.contains("-zh") || id.contains("zh-") -> Locale.CHINA
                else -> Regex("""-([a-z]{2})-""").find(id)?.let { Locale(it.groupValues[1]) } ?: Locale.ROOT
            }
        }

        /** "vits-piper-en_US-amy-medium" → "Amy (medium)". */
        fun describeVoice(id: String): String {
            if (id.startsWith("vits-piper-")) {
                val parts = id.removePrefix("vits-piper-").split('-')
                val voice = parts.getOrNull(1)?.replace('_', ' ')?.replaceFirstChar { it.titlecase() } ?: id
                val quality = parts.getOrNull(2)?.replace('_', '-')
                return if (quality != null) "$voice ($quality)" else voice
            }
            return describe(id)
        }

        fun describe(id: String): String = when {
            id.startsWith("vits-piper-") -> "Piper ${packLocale(id).toLanguageTag()} · ${describeVoice(id)}"
            id.contains("kokoro") -> "Kokoro " + id.substringAfter("kokoro-").replace('_', '.').replace('-', ' ')
            id.contains("kitten") -> "Kitten " + id.substringAfter("kitten-").replace('_', '.').replace('-', ' ')
            id.contains("supertonic") -> "Supertonic 3 (31 languages)"
            else -> id
        }

        /** Works out what kind of model a freshly unpacked directory holds, or explains why it can't be used. */
        fun detect(dir: File): VoicePack {
            val files = dir.listFiles().orEmpty()
            fun has(name: String) = files.any { it.name == name }
            val onnx = files.filter { it.isFile && it.name.endsWith(".onnx") }.map { it.name }.sorted()
            val dataDir = if (File(dir, "espeak-ng-data").isDirectory) "espeak-ng-data" else ""
            val preferred = listOf("phone-zh.fst", "date-zh.fst", "number-zh.fst")
            val fsts = files.filter { it.name.endsWith(".fst") }.map { it.name }.sortedBy { preferred.indexOf(it).let { i -> if (i < 0) 99 else i } }
            val fars = files.filter { it.name.endsWith(".far") }.map { it.name }.sorted()

            if (has("tts.json") && has("unicode_indexer.bin")) {
                fun role(prefix: String) = onnx.firstOrNull { it.startsWith(prefix) }
                    ?: throw IllegalArgumentException("Supertonic pack is missing $prefix*.onnx")
                return VoicePack(
                    dir, PackKind.SUPERTONIC,
                    extraFiles = mapOf(
                        "durationPredictor" to role("duration_predictor"), "textEncoder" to role("text_encoder"),
                        "vectorEstimator" to role("vector_estimator"), "vocoder" to role("vocoder"),
                        "ttsJson" to "tts.json", "unicodeIndexer" to "unicode_indexer.bin",
                        "voiceStyle" to (files.firstOrNull { it.name.startsWith("voice") && it.name.endsWith(".bin") }?.name
                            ?: throw IllegalArgumentException("Supertonic pack is missing voice.bin")),
                    ),
                )
            }

            require(has("tokens.txt")) { "Not a supported voice pack (no tokens.txt). Use a sherpa-onnx TTS model archive." }
            require(onnx.isNotEmpty()) { "No .onnx model file found in the pack." }

            if (has("voices.bin")) {
                val model = onnx.firstOrNull { it.startsWith("model") } ?: onnx.first()
                val allLexicons = files.map { it.name }.filter { it.startsWith("lexicon") && it.endsWith(".txt") }.sorted()
                val lexicons = if ("lexicon-us-en.txt" in allLexicons) {
                    listOf("lexicon-us-en.txt") + allLexicons.filter { it == "lexicon-zh.txt" }
                } else allLexicons
                val kitten = dir.name.contains("kitten")
                return VoicePack(
                    dir, if (kitten) PackKind.KITTEN else PackKind.KOKORO, model = model, tokens = "tokens.txt",
                    voices = "voices.bin", lexicons = if (kitten) emptyList() else lexicons, dataDir = dataDir,
                    ruleFsts = if (kitten) emptyList() else fsts,
                )
            }

            val vocoder = onnx.firstOrNull { it.contains("vocos") || it.contains("hifigan") || it.contains("vocoder") }
            val lexicon = listOfNotNull(files.firstOrNull { it.name == "lexicon.txt" }?.name)
            if (vocoder != null) {
                val acoustic = onnx.first { it != vocoder }
                return VoicePack(dir, PackKind.MATCHA, model = acoustic, tokens = "tokens.txt", vocoder = vocoder,
                    lexicons = lexicon, dataDir = dataDir, ruleFsts = fsts, ruleFars = fars)
            }
            require(!(dir.name.startsWith("matcha") && onnx.size == 1)) {
                "Matcha models need a separate vocoder file (e.g. vocos-22khz-univ.onnx) placed in the same archive."
            }
            val model = onnx.firstOrNull { !it.contains("int8") } ?: onnx.first()
            return VoicePack(dir, PackKind.VITS, model = model, tokens = "tokens.txt", lexicons = lexicon,
                dataDir = dataDir, ruleFsts = fsts, ruleFars = fars)
        }
    }
}
