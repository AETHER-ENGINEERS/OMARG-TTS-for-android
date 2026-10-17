package com.aetherengineers.onarg.audio

import com.aetherengineers.onarg.audio.dsp.Biquad
import kotlin.math.floor

object Resampler {

    /** Converts between sample rates, keeping pitch and duration. */
    fun convert(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate || input.isEmpty()) return input.copyOf()
        return readAtStep(input, fromRate.toDouble() / toRate, fromRate)
    }

    /**
     * Reads [input] advancing [step] input samples per output sample (cubic Hermite interpolation).
     * step > 1 shortens and raises pitch, step < 1 lengthens and lowers pitch.
     */
    fun readAtStep(input: FloatArray, step: Double, sampleRate: Int): FloatArray {
        if (input.isEmpty()) return input
        val src = if (step > 1.0) {
            // Anti-alias before decimating.
            val cutoff = (sampleRate / 2.0 / step) * 0.9
            val a = Biquad.lowPass(sampleRate, cutoff, 0.707)
            val b = Biquad.lowPass(sampleRate, cutoff, 0.707)
            FloatArray(input.size) { b.process(a.process(input[it])) }
        } else input
        val outLen = floor((src.size - 1) / step).toInt() + 1
        val out = FloatArray(outLen)
        val last = src.size - 1
        for (i in 0 until outLen) {
            val pos = i * step
            val idx = pos.toInt()
            val t = (pos - idx).toFloat()
            val y0 = src[(idx - 1).coerceIn(0, last)]
            val y1 = src[idx.coerceIn(0, last)]
            val y2 = src[(idx + 1).coerceIn(0, last)]
            val y3 = src[(idx + 2).coerceIn(0, last)]
            val c1 = 0.5f * (y2 - y0)
            val c2 = y0 - 2.5f * y1 + 2f * y2 - 0.5f * y3
            val c3 = 0.5f * (y3 - y0) + 1.5f * (y1 - y2)
            out[i] = ((c3 * t + c2) * t + c1) * t + y1
        }
        return out
    }
}
