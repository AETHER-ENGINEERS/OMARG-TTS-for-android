package com.aetherengineers.onarg.tts

/**
 * A narration script split into synthesizable pieces.
 *
 * Supported inline tags (case-insensitive):
 *  - `[pause 1.5]`, `[pause 800ms]`, `[pause 2s]` — insert silence
 *  - `[1]` … `[4]` — switch to cast voice 1–4 for dialogue
 */
sealed interface ScriptPart {
    data class Speech(val text: String, val voiceSlot: Int) : ScriptPart
    data class Pause(val seconds: Double) : ScriptPart
}

object Script {
    private val tag = Regex("""\[\s*(pause\s*([0-9]*\.?[0-9]+)\s*(ms|s)?|([1-4]))\s*]""", RegexOption.IGNORE_CASE)

    fun parse(text: String, maxChunk: Int = 1500): List<ScriptPart> {
        val parts = mutableListOf<ScriptPart>()
        var slot = 0
        var last = 0
        fun flush(segment: String) {
            for (chunk in chunk(segment, maxChunk)) parts += ScriptPart.Speech(chunk, slot)
        }
        for (m in tag.findAll(text)) {
            flush(text.substring(last, m.range.first))
            last = m.range.last + 1
            val voice = m.groupValues[4]
            if (voice.isNotEmpty()) {
                slot = voice.toInt() - 1
            } else {
                val n = m.groupValues[2].toDouble()
                val seconds = if (m.groupValues[3].equals("ms", true)) n / 1000 else n
                parts += ScriptPart.Pause(seconds.coerceIn(0.0, 60.0))
            }
        }
        flush(text.substring(last))
        return parts
    }

    /** Splits text into chunks no longer than [max], preferring paragraph, sentence, then word boundaries. */
    fun chunk(text: String, max: Int): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.length <= max) return listOf(trimmed)
        val out = mutableListOf<String>()
        var rest = trimmed
        while (rest.length > max) {
            val window = rest.substring(0, max)
            val cut = listOf("\n\n", ". ", "! ", "? ", "; ", ", ", "\n", " ")
                .firstNotNullOfOrNull { sep -> window.lastIndexOf(sep).takeIf { it > max / 3 }?.plus(sep.length) }
                ?: max
            out += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) out += rest
        return out.filter { it.isNotEmpty() }
    }

    /** Text with all tags removed — for character counts and titles. */
    fun plain(text: String) = tag.replace(text, " ").replace(Regex("\\s+"), " ").trim()
}
