package com.aetherengineers.omarg.audio

import com.aetherengineers.omarg.audio.dsp.Dsp
import kotlin.math.min

object Mixer {

    /**
     * Lays [music] under [voice]. Music is looped to cover the voice plus [tailSeconds],
     * ducked while the voice is speaking, and faded out at the end.
     */
    fun mixBackground(
        voice: AudioBuffer,
        music: AudioBuffer,
        volume: Float,
        duck: Boolean,
        loop: Boolean,
        leadSeconds: Double = 0.6,
        tailSeconds: Double = 1.5,
    ): AudioBuffer {
        val sr = voice.sampleRate
        val bed = if (music.sampleRate == sr) music.samples else Resampler.convert(music.samples, music.sampleRate, sr)
        if (bed.isEmpty()) return voice
        val lead = (leadSeconds * sr).toInt()
        val total = lead + voice.size + (tailSeconds * sr).toInt()
        val env = if (duck) Dsp.envelope(voice.samples, sr, 30.0, 600.0) else null
        val out = FloatArray(total)
        val fadeOut = min(total, (tailSeconds * sr).toInt().coerceAtLeast(1))
        for (i in 0 until total) {
            val m = when {
                i < bed.size -> bed[i]
                loop -> bed[i % bed.size]
                else -> 0f
            }
            val v = i - lead
            val voiceSample = if (v in voice.samples.indices) voice.samples[v] else 0f
            val duckGain = if (env != null && v in env.indices) 1f - 0.65f * min(1f, env[v] * 6f) else 1f
            val fade = if (i >= total - fadeOut) (total - i).toFloat() / fadeOut else 1f
            val fadeIn = if (i < lead) i.toFloat() / lead else 1f
            out[i] = voiceSample + m * volume * duckGain * fade * fadeIn
        }
        return AudioBuffer(out, sr)
    }
}
