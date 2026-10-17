package com.aetherengineers.omarg.audio.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** RBJ cookbook biquad filter, transposed direct form II. */
class Biquad private constructor(
    private val b0: Double, private val b1: Double, private val b2: Double,
    private val a1: Double, private val a2: Double,
) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun process(x: Float): Float {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y.toFloat()
    }

    fun processAll(samples: FloatArray): FloatArray = FloatArray(samples.size) { process(samples[it]) }

    companion object {
        private fun make(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
            Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

        private fun w0(sr: Int, f: Double) = 2 * PI * f.coerceIn(10.0, sr * 0.49) / sr

        fun lowPass(sr: Int, freq: Double, q: Double = 0.707): Biquad {
            val w = w0(sr, freq); val c = cos(w); val alpha = sin(w) / (2 * q)
            return make((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha)
        }

        fun highPass(sr: Int, freq: Double, q: Double = 0.707): Biquad {
            val w = w0(sr, freq); val c = cos(w); val alpha = sin(w) / (2 * q)
            return make((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha)
        }

        fun bandPass(sr: Int, freq: Double, q: Double): Biquad {
            val w = w0(sr, freq); val c = cos(w); val alpha = sin(w) / (2 * q)
            return make(alpha, 0.0, -alpha, 1 + alpha, -2 * c, 1 - alpha)
        }

        fun peaking(sr: Int, freq: Double, q: Double, gainDb: Double): Biquad {
            val a = 10.0.pow(gainDb / 40); val w = w0(sr, freq); val c = cos(w); val alpha = sin(w) / (2 * q)
            return make(1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a)
        }

        fun lowShelf(sr: Int, freq: Double, gainDb: Double): Biquad {
            val a = 10.0.pow(gainDb / 40); val w = w0(sr, freq); val c = cos(w)
            val alpha = sin(w) / 2 * sqrt(2.0); val sa = 2 * sqrt(a) * alpha
            return make(
                a * ((a + 1) - (a - 1) * c + sa), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - sa),
                (a + 1) + (a - 1) * c + sa, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - sa,
            )
        }

        fun highShelf(sr: Int, freq: Double, gainDb: Double): Biquad {
            val a = 10.0.pow(gainDb / 40); val w = w0(sr, freq); val c = cos(w)
            val alpha = sin(w) / 2 * sqrt(2.0); val sa = 2 * sqrt(a) * alpha
            return make(
                a * ((a + 1) + (a - 1) * c + sa), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - sa),
                (a + 1) - (a - 1) * c + sa, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - sa,
            )
        }
    }
}
