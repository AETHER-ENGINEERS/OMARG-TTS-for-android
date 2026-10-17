package com.aetherengineers.onarg.audio.dsp

import com.aetherengineers.onarg.audio.AudioBuffer

class ParamSpec(val key: String, val label: String, val min: Double, val max: Double, val default: Double, val unit: String = "", val step: Double = 0.0)

/** Every building block a voice effect chain can use. Names are persisted — never rename them. */
enum class EffectType(val label: String, val params: List<ParamSpec>) {
    PITCH("Pitch", listOf(ParamSpec("semitones", "Semitones", -12.0, 12.0, 0.0, "st", 0.5))),
    TEMPO("Tempo", listOf(ParamSpec("tempo", "Tempo", 0.5, 2.0, 1.0, "×"))),
    SPEED("Tape speed", listOf(ParamSpec("rate", "Rate", 0.5, 2.0, 1.0, "×"))),
    REVERB("Reverb", listOf(
        ParamSpec("room", "Room size", 0.0, 1.0, 0.6),
        ParamSpec("damping", "Damping", 0.0, 1.0, 0.5),
        ParamSpec("wet", "Wet", 0.0, 1.0, 0.3),
        ParamSpec("dry", "Dry", 0.0, 1.0, 1.0),
        ParamSpec("predelay", "Pre-delay", 0.0, 200.0, 20.0, "ms"),
    )),
    ECHO("Echo", listOf(
        ParamSpec("delay", "Delay", 20.0, 1500.0, 300.0, "ms"),
        ParamSpec("feedback", "Feedback", 0.0, 0.9, 0.4),
        ParamSpec("mix", "Mix", 0.0, 1.0, 0.5),
    )),
    CHORUS("Chorus", listOf(
        ParamSpec("depth", "Depth", 0.5, 20.0, 6.0, "ms"),
        ParamSpec("rate", "Rate", 0.05, 5.0, 0.8, "Hz"),
        ParamSpec("voices", "Voices", 1.0, 6.0, 3.0, "", 1.0),
        ParamSpec("mix", "Mix", 0.0, 1.0, 0.5),
    )),
    FLANGER("Flanger", listOf(
        ParamSpec("depth", "Depth", 0.1, 10.0, 3.0, "ms"),
        ParamSpec("rate", "Rate", 0.05, 5.0, 0.25, "Hz"),
        ParamSpec("feedback", "Feedback", -0.9, 0.9, 0.6),
        ParamSpec("mix", "Mix", 0.0, 1.0, 0.6),
    )),
    VIBRATO("Vibrato / wobble", listOf(
        ParamSpec("depth", "Depth", 0.1, 15.0, 2.0, "ms"),
        ParamSpec("rate", "Rate", 0.1, 12.0, 5.0, "Hz"),
    )),
    TREMOLO("Tremolo", listOf(
        ParamSpec("rate", "Rate", 0.5, 30.0, 6.0, "Hz"),
        ParamSpec("depth", "Depth", 0.0, 1.0, 0.6),
    )),
    RING_MOD("Ring modulator", listOf(
        ParamSpec("freq", "Carrier", 5.0, 2000.0, 60.0, "Hz"),
        ParamSpec("mix", "Mix", 0.0, 1.0, 1.0),
    )),
    METALLIC("Metallic resonance", listOf(
        ParamSpec("delay", "Resonance", 1.0, 20.0, 5.0, "ms"),
        ParamSpec("feedback", "Feedback", 0.0, 0.95, 0.7),
        ParamSpec("mix", "Mix", 0.0, 1.0, 0.5),
    )),
    DISTORTION("Distortion", listOf(
        ParamSpec("drive", "Drive", 1.0, 30.0, 4.0),
        ParamSpec("mix", "Mix", 0.0, 1.0, 1.0),
    )),
    BITCRUSH("Bitcrusher", listOf(
        ParamSpec("bits", "Bit depth", 2.0, 16.0, 8.0, "bit", 1.0),
        ParamSpec("rate", "Sample rate", 1000.0, 24000.0, 8000.0, "Hz"),
    )),
    FILTER("Band filter", listOf(
        ParamSpec("low", "Low cut", 20.0, 2000.0, 20.0, "Hz"),
        ParamSpec("high", "High cut", 500.0, 20000.0, 20000.0, "Hz"),
    )),
    EQ("Equalizer", listOf(
        ParamSpec("bass", "Bass", -15.0, 15.0, 0.0, "dB"),
        ParamSpec("mid", "Mid", -15.0, 15.0, 0.0, "dB"),
        ParamSpec("treble", "Treble", -15.0, 15.0, 0.0, "dB"),
    )),
    COMPRESSOR("Compressor", listOf(
        ParamSpec("threshold", "Threshold", -50.0, 0.0, -20.0, "dB"),
        ParamSpec("ratio", "Ratio", 1.0, 20.0, 4.0, ":1"),
        ParamSpec("makeup", "Makeup", 0.0, 24.0, 6.0, "dB"),
    )),
    NOISE("Static / noise", listOf(
        ParamSpec("level", "Level", -60.0, -10.0, -30.0, "dB"),
        ParamSpec("low", "Low cut", 20.0, 4000.0, 300.0, "Hz"),
        ParamSpec("high", "High cut", 1000.0, 20000.0, 6000.0, "Hz"),
    )),
    LAYER("Voice layer", listOf(
        ParamSpec("semitones", "Pitch", -24.0, 24.0, -12.0, "st", 1.0),
        ParamSpec("level", "Level", 0.0, 1.5, 0.6),
        ParamSpec("delay", "Delay", 0.0, 200.0, 0.0, "ms"),
    )),
    REVERSE("Reverse", emptyList()),
    GAIN("Volume", listOf(ParamSpec("db", "Gain", -24.0, 24.0, 0.0, "dB"))),
    FADE("Fade in/out", listOf(
        ParamSpec("in", "Fade in", 0.0, 5000.0, 300.0, "ms"),
        ParamSpec("out", "Fade out", 0.0, 5000.0, 600.0, "ms"),
    ));

    fun defaults(): Map<String, Double> = params.associate { it.key to it.default }
}

data class EffectStep(val type: EffectType, val params: Map<String, Double> = type.defaults(), val enabled: Boolean = true) {
    operator fun get(key: String): Double = params[key] ?: type.params.first { it.key == key }.default

    fun apply(input: AudioBuffer): AudioBuffer {
        if (!enabled) return input
        val x = input.samples
        val sr = input.sampleRate
        val out = when (type) {
            EffectType.PITCH -> Dsp.pitchShift(x, sr, this["semitones"])
            EffectType.TEMPO -> Dsp.timeStretch(x, sr, this["tempo"])
            EffectType.SPEED -> Dsp.speed(x, sr, this["rate"])
            EffectType.REVERB -> Dsp.reverb(x, sr, this["room"], this["damping"], this["wet"], this["dry"], this["predelay"])
            EffectType.ECHO -> Dsp.echo(x, sr, this["delay"], this["feedback"], this["mix"])
            EffectType.CHORUS -> Dsp.modDelay(x, sr, 15.0, this["depth"], this["rate"], 0.0, this["mix"], 1.0, this["voices"].toInt())
            EffectType.FLANGER -> Dsp.modDelay(x, sr, 0.5, this["depth"], this["rate"], this["feedback"], this["mix"], 1.0)
            EffectType.VIBRATO -> Dsp.modDelay(x, sr, 1.0, this["depth"], this["rate"], 0.0, 1.0, 0.0)
            EffectType.TREMOLO -> Dsp.tremolo(x, sr, this["rate"], this["depth"])
            EffectType.RING_MOD -> Dsp.ringMod(x, sr, this["freq"], this["mix"])
            EffectType.METALLIC -> Dsp.modDelay(x, sr, this["delay"], 0.0, 0.0, this["feedback"], this["mix"], 1.0)
            EffectType.DISTORTION -> Dsp.distortion(x, this["drive"], this["mix"])
            EffectType.BITCRUSH -> Dsp.bitcrush(x, sr, this["bits"], this["rate"])
            EffectType.FILTER -> Dsp.filter(x, sr, this["low"], this["high"])
            EffectType.EQ -> Dsp.equalizer(x, sr, this["bass"], this["mid"], this["treble"])
            EffectType.COMPRESSOR -> Dsp.compressor(x, sr, this["threshold"], this["ratio"], 5.0, 120.0, this["makeup"])
            EffectType.NOISE -> Dsp.noise(x, sr, this["level"], this["low"], this["high"])
            EffectType.LAYER -> Dsp.layer(x, sr, this["semitones"], this["level"], this["delay"])
            EffectType.REVERSE -> Dsp.reverse(x)
            EffectType.GAIN -> Dsp.gain(x, this["db"])
            EffectType.FADE -> Dsp.fade(x, sr, this["in"], this["out"])
        }
        return AudioBuffer(out, sr)
    }
}

data class EffectChain(val name: String, val steps: List<EffectStep>, val category: String = "Custom", val builtIn: Boolean = false) {

    fun process(input: AudioBuffer, onProgress: (Float) -> Unit = {}): AudioBuffer {
        var buf = input
        val active = steps.filter { it.enabled }
        active.forEachIndexed { i, step ->
            buf = step.apply(buf)
            onProgress((i + 1f) / active.size)
        }
        return buf
    }

    /** Line-based text format: first line is the name, then `TYPE key=value key=value [off]`. */
    fun encode(): String = buildString {
        append(name.replace('\n', ' '))
        for (s in steps) {
            append('\n').append(s.type.name)
            for ((k, v) in s.params) append(' ').append(k).append('=').append(v)
            if (!s.enabled) append(" off")
        }
    }

    companion object {
        fun decode(text: String): EffectChain? {
            val lines = text.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) return null
            val steps = lines.drop(1).mapNotNull { line ->
                val parts = line.trim().split(' ')
                val type = runCatching { EffectType.valueOf(parts[0]) }.getOrNull() ?: return@mapNotNull null
                val params = type.defaults().toMutableMap()
                var enabled = true
                for (p in parts.drop(1)) {
                    if (p == "off") { enabled = false; continue }
                    val kv = p.split('=')
                    if (kv.size == 2 && kv[0] in params) kv[1].toDoubleOrNull()?.let { params[kv[0]] = it }
                }
                EffectStep(type, params, enabled)
            }
            return EffectChain(lines[0], steps)
        }
    }
}
