package com.aetherengineers.omarg.tts.neural

/** A recommended voice pack. Downloads happen in the user's browser — the app itself never goes online. */
data class CatalogEntry(val id: String, val title: String, val description: String, val size: String, val group: String) {
    val url: String get() = "$RELEASES/$id.tar.bz2"

    companion object {
        const val RELEASES = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"
        const val ALL_MODELS = "https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models"
    }
}

object VoiceCatalog {
    private fun piper(locale: String, voice: String, quality: String, note: String) = CatalogEntry(
        "vits-piper-$locale-$voice-$quality",
        "${voice.replace('_', ' ').replaceFirstChar { it.titlecase() }} · $locale",
        note, if (quality == "high") "Medium download" else "Small download", "Piper — one natural voice per pack",
    )

    val entries: List<CatalogEntry> = listOf(
        CatalogEntry("kokoro-multi-lang-v1_0", "Kokoro v1.0 — 53 voices",
            "Top quality. US & UK English, Spanish, French, Hindi, Italian, Japanese, Brazilian Portuguese and Chinese voices in one pack.",
            "Large download", "Best quality"),
        CatalogEntry("kokoro-en-v0_19", "Kokoro v0.19 — 11 English voices",
            "Very natural American and British English voices.", "Large download", "Best quality"),
        CatalogEntry("kokoro-int8-multi-lang-v1_1", "Kokoro v1.1 (compact)",
            "Smaller, faster Kokoro with English and 100+ Chinese voices.", "Medium download", "Best quality"),
        CatalogEntry("sherpa-onnx-supertonic-3-tts-int8-2026-05-11", "Supertonic 3 — 31 languages",
            "One multilingual model: English, Korean, Japanese, Arabic, German, Spanish, French, Hindi, Italian, Dutch, Polish, " +
                "Portuguese, Russian, Turkish, Ukrainian, Vietnamese and more.", "Large download", "Many languages"),
        CatalogEntry("kitten-mini-en-v0_8", "Kitten Mini — 8 English voices",
            "Expressive, lightweight English voices. Fast even on long texts.", "Small download", "Small & fast"),
        CatalogEntry("kitten-nano-en-v0_8-int8", "Kitten Nano — 8 English voices",
            "Tiny download, quick to render.", "Small download", "Small & fast"),
        piper("en_US", "glados", "high", "The GLaDOS character voice.").copy(group = "Characters"),
        piper("de_DE", "glados", "high", "GLaDOS, in German.").copy(group = "Characters"),
        piper("en_US", "lessac", "high", "Clear, warm US English narrator."),
        piper("en_US", "ryan", "high", "Deep US English male voice — great for trailers."),
        piper("en_US", "norman", "medium", "Classic storyteller voice."),
        piper("en_US", "libritts_r", "medium", "900+ different US English speakers in one pack."),
        piper("en_US", "amy", "medium", "Friendly US English female voice."),
        piper("en_GB", "cori", "high", "British English female voice."),
        piper("en_GB", "alan", "medium", "British English male voice."),
        piper("es_ES", "davefx", "medium", "Spanish (Spain)."),
        piper("es_MX", "claude", "high", "Spanish (Mexico)."),
        piper("fr_FR", "siwis", "medium", "French."),
        piper("fr_FR", "tom", "medium", "French male voice."),
        piper("de_DE", "thorsten", "high", "German."),
        piper("it_IT", "paola", "medium", "Italian."),
        piper("pt_BR", "faber", "medium", "Brazilian Portuguese."),
        piper("pt_PT", "tugao", "medium", "European Portuguese."),
        piper("nl_NL", "mls", "medium", "Dutch."),
        piper("pl_PL", "gosia", "medium", "Polish."),
        piper("ru_RU", "irina", "medium", "Russian."),
        piper("uk_UA", "ukrainian_tts", "medium", "Ukrainian (several speakers)."),
        piper("tr_TR", "fahrettin", "medium", "Turkish."),
        piper("sv_SE", "nst", "medium", "Swedish."),
        piper("hi_IN", "pratham", "medium", "Hindi."),
        piper("ar_JO", "kareem", "medium", "Arabic."),
        piper("vi_VN", "vais1000", "medium", "Vietnamese."),
        piper("zh_CN", "huayan", "medium", "Mandarin Chinese."),
    )

    val groups: List<String> = entries.map { it.group }.distinct()
}
