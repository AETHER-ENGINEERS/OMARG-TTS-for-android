package com.aetherengineers.onarg.audio

/** Mono floating-point audio in the range [-1, 1]. */
class AudioBuffer(val samples: FloatArray, val sampleRate: Int) {
    val size: Int get() = samples.size
    val durationSeconds: Double get() = samples.size.toDouble() / sampleRate

    fun copy(samples: FloatArray = this.samples.copyOf()) = AudioBuffer(samples, sampleRate)

    companion object {
        fun silence(seconds: Double, sampleRate: Int) =
            AudioBuffer(FloatArray((seconds * sampleRate).toInt().coerceAtLeast(0)), sampleRate)

        /** Concatenates buffers, resampling each to the sample rate of the first. */
        fun concat(parts: List<AudioBuffer>, sampleRate: Int = parts.firstOrNull()?.sampleRate ?: 24000): AudioBuffer {
            val converted = parts.map { if (it.sampleRate == sampleRate) it.samples else Resampler.convert(it.samples, it.sampleRate, sampleRate) }
            val out = FloatArray(converted.sumOf { it.size })
            var pos = 0
            for (c in converted) {
                c.copyInto(out, pos)
                pos += c.size
            }
            return AudioBuffer(out, sampleRate)
        }
    }
}
