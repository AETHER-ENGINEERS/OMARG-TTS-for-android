package com.aetherengineers.omarg.audio.dsp

import com.aetherengineers.omarg.audio.Resampler
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/** Pure-Kotlin offline audio processors. Every function returns a new array. */
object Dsp {

    fun dbToGain(db: Double) = 10.0.pow(db / 20).toFloat()

    fun withTail(x: FloatArray, tailSamples: Int): FloatArray = x.copyOf(x.size + max(0, tailSamples))

    // ---------------------------------------------------------------- time & pitch

    /**
     * WSOLA time stretch. [tempo] > 1 makes audio shorter/faster, < 1 longer/slower; pitch is unchanged.
     */
    fun timeStretch(x: FloatArray, sampleRate: Int, tempo: Double): FloatArray {
        if (x.isEmpty() || abs(tempo - 1.0) < 1e-4) return x.copyOf()
        val n = ((0.040 * sampleRate).toInt() / 2) * 2
        val hs = n / 2
        val ha = hs * tempo
        val tol = (0.012 * sampleRate).toInt()
        val window = FloatArray(n) { (0.5 - 0.5 * cos(2 * PI * it / n)).toFloat() }
        val src = x.copyOf(x.size + n + 2 * tol)
        val outLen = (x.size / tempo).roundToInt()
        val out = FloatArray(outLen + n)
        var prevStart = 0
        var k = 0
        while (k * hs < outLen) {
            val nominal = (k * ha).roundToInt()
            val start = if (k == 0) 0 else {
                val natural = prevStart + hs
                val lo = max(0, nominal - tol)
                val hi = nominal + tol
                var best = nominal.coerceAtLeast(0)
                var bestScore = Double.NEGATIVE_INFINITY
                var s = lo
                while (s <= hi) {
                    var dot = 0.0
                    var i = 0
                    while (i < hs) {
                        dot += src[natural + i] * src[s + i]
                        i += 2
                    }
                    if (dot > bestScore) { bestScore = dot; best = s }
                    s += 2
                }
                best
            }
            if (start + n > src.size) break
            val o = k * hs
            for (i in 0 until n) out[o + i] += src[start + i] * window[i]
            prevStart = start
            k++
        }
        return out.copyOf(outLen)
    }

    /** Shift pitch by [semitones] keeping duration. */
    fun pitchShift(x: FloatArray, sampleRate: Int, semitones: Double): FloatArray {
        if (abs(semitones) < 0.01) return x.copyOf()
        val f = 2.0.pow(semitones / 12.0)
        val stretched = timeStretch(x, sampleRate, 1.0 / f)
        val out = Resampler.readAtStep(stretched, f, sampleRate)
        return out.copyOf(x.size)
    }

    /** Tape-style speed change: pitch and tempo move together. */
    fun speed(x: FloatArray, sampleRate: Int, rate: Double): FloatArray =
        if (abs(rate - 1.0) < 1e-4) x.copyOf() else Resampler.readAtStep(x, rate, sampleRate)

    fun reverse(x: FloatArray): FloatArray = x.reversedArray()

    // ---------------------------------------------------------------- space

    /** Freeverb (Jezar) — 8 parallel combs into 4 series all-passes. */
    fun reverb(x: FloatArray, sampleRate: Int, room: Double, damping: Double, wet: Double, dry: Double, preDelayMs: Double): FloatArray {
        val scale = sampleRate / 44100.0
        val tail = (sampleRate * (0.6 + room * 4.0)).toInt()
        val pre = (preDelayMs / 1000 * sampleRate).toInt()
        val input = withTail(x, tail + pre)
        val feedback = (room * 0.28 + 0.7).toFloat().coerceAtMost(0.985f)
        val damp = (damping * 0.4).toFloat()
        val combs = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617).map { Comb((it * scale).toInt(), feedback, damp) }
        val allPasses = intArrayOf(556, 441, 341, 225).map { AllPass((it * scale).toInt(), 0.5f) }
        val out = FloatArray(input.size)
        val g = 0.015f
        val w = wet.toFloat() * 3f
        val d = dry.toFloat()
        for (i in input.indices) {
            val inp = (if (i - pre >= 0) input[i - pre] else 0f) * g
            var acc = 0f
            for (c in combs) acc += c.process(inp)
            for (a in allPasses) acc = a.process(acc)
            out[i] = input[i] * d + acc * w
        }
        return trimTail(out, x.size)
    }

    private class Comb(size: Int, val feedback: Float, val damp: Float) {
        val buf = FloatArray(max(1, size)); var idx = 0; var store = 0f
        fun process(x: Float): Float {
            val y = buf[idx]
            store = y * (1 - damp) + store * damp
            buf[idx] = x + store * feedback
            if (++idx >= buf.size) idx = 0
            return y
        }
    }

    private class AllPass(size: Int, val feedback: Float) {
        val buf = FloatArray(max(1, size)); var idx = 0
        fun process(x: Float): Float {
            val b = buf[idx]
            buf[idx] = x + b * feedback
            if (++idx >= buf.size) idx = 0
            return b - x
        }
    }

    fun echo(x: FloatArray, sampleRate: Int, delayMs: Double, feedback: Double, mix: Double): FloatArray {
        val delay = max(1, (delayMs / 1000 * sampleRate).toInt())
        val fb = feedback.coerceIn(0.0, 0.95)
        val repeats = if (fb <= 0.001) 1.0 else min(60.0, ln(0.001) / ln(fb))
        val out = withTail(x, min((delay * (repeats + 1)).toInt(), sampleRate * 12))
        val line = FloatArray(delay)
        var idx = 0
        for (i in out.indices) {
            val delayed = line[idx]
            val dryS = out[i]
            line[idx] = dryS + delayed * fb.toFloat()
            if (++idx >= delay) idx = 0
            out[i] = dryS + delayed * mix.toFloat()
        }
        return trimTail(out, x.size)
    }

    /**
     * Modulated delay line — chorus, flanger and vibrato are all parameterisations of this.
     * [voices] spreads LFO phases for a thicker chorus.
     */
    fun modDelay(
        x: FloatArray, sampleRate: Int, baseMs: Double, depthMs: Double, rateHz: Double,
        feedback: Double, mix: Double, dry: Double, voices: Int = 1,
    ): FloatArray {
        val maxDelay = ((baseMs + depthMs) / 1000 * sampleRate).toInt() + 4
        val size = maxDelay + 1
        val line = FloatArray(size)
        val out = withTail(x, maxDelay)
        val v = voices.coerceIn(1, 6)
        var w = 0
        val fb = feedback.toFloat().coerceIn(-0.95f, 0.95f)
        for (i in out.indices) {
            val input = out[i]
            var wetSum = 0f
            for (k in 0 until v) {
                val phase = 2 * PI * (rateHz * (1 + 0.13 * k) * i / sampleRate) + 2 * PI * k / v
                val delaySamples = (baseMs + depthMs * 0.5 * (1 + sin(phase))) / 1000 * sampleRate
                var r = w - delaySamples
                while (r < 0) r += size
                val i0 = r.toInt() % size
                val i1 = (i0 + 1) % size
                val frac = (r - r.toInt()).toFloat()
                wetSum += line[i0] * (1 - frac) + line[i1] * frac
            }
            wetSum /= v
            line[w] = input + wetSum * fb
            if (++w >= size) w = 0
            out[i] = input * dry.toFloat() + wetSum * mix.toFloat()
        }
        return trimTail(out, x.size)
    }

    // ---------------------------------------------------------------- modulation & color

    fun ringMod(x: FloatArray, sampleRate: Int, freq: Double, mix: Double): FloatArray {
        val m = mix.toFloat()
        return FloatArray(x.size) {
            val carrier = sin(2 * PI * freq * it / sampleRate).toFloat()
            x[it] * (1 - m) + x[it] * carrier * m
        }
    }

    fun tremolo(x: FloatArray, sampleRate: Int, rateHz: Double, depth: Double): FloatArray =
        FloatArray(x.size) {
            val lfo = 0.5 + 0.5 * sin(2 * PI * rateHz * it / sampleRate)
            (x[it] * (1 - depth * lfo)).toFloat()
        }

    fun distortion(x: FloatArray, drive: Double, mix: Double): FloatArray {
        val d = max(0.01, drive)
        val norm = tanh(d)
        val m = mix.toFloat()
        return FloatArray(x.size) { (x[it] * (1 - m) + (tanh(d * x[it]) / norm).toFloat() * m) }
    }

    fun bitcrush(x: FloatArray, sampleRate: Int, bits: Double, targetRate: Double): FloatArray {
        val levels = 2.0.pow(bits.coerceIn(1.0, 16.0)).toFloat() / 2
        val hold = max(1.0, sampleRate / targetRate.coerceAtLeast(500.0))
        val out = FloatArray(x.size)
        var counter = 0.0
        var held = 0f
        for (i in x.indices) {
            if (counter <= 0) { held = (x[i] * levels).roundToInt() / levels; counter += hold }
            counter -= 1
            out[i] = held
        }
        return out
    }

    fun noise(x: FloatArray, sampleRate: Int, levelDb: Double, lowCut: Double, highCut: Double, seed: Int = 7): FloatArray {
        val rnd = Random(seed)
        val hp = Biquad.highPass(sampleRate, lowCut)
        val lp = Biquad.lowPass(sampleRate, highCut)
        val g = dbToGain(levelDb)
        val gate = envelope(x, sampleRate, 5.0, 400.0)
        return FloatArray(x.size) {
            val n = lp.process(hp.process(rnd.nextFloat() * 2 - 1)) * g
            // Static is louder while speaking, like a real transmission, but never fully silent.
            x[it] + n * (0.35f + 0.65f * min(1f, gate[it] * 4))
        }
    }

    fun filter(x: FloatArray, sampleRate: Int, lowCut: Double, highCut: Double): FloatArray {
        var y = x
        if (lowCut > 20) { val a = Biquad.highPass(sampleRate, lowCut); val b = Biquad.highPass(sampleRate, lowCut); y = FloatArray(y.size) { b.process(a.process(x[it])) } }
        if (highCut < sampleRate / 2.0 - 100) { val a = Biquad.lowPass(sampleRate, highCut); val b = Biquad.lowPass(sampleRate, highCut); val s = y; y = FloatArray(s.size) { b.process(a.process(s[it])) } }
        return if (y === x) x.copyOf() else y
    }

    fun equalizer(x: FloatArray, sampleRate: Int, bassDb: Double, midDb: Double, trebleDb: Double): FloatArray {
        val low = Biquad.lowShelf(sampleRate, 200.0, bassDb)
        val mid = Biquad.peaking(sampleRate, 1500.0, 0.8, midDb)
        val high = Biquad.highShelf(sampleRate, 5000.0, trebleDb)
        return FloatArray(x.size) { high.process(mid.process(low.process(x[it]))) }
    }

    // ---------------------------------------------------------------- dynamics

    fun envelope(x: FloatArray, sampleRate: Int, attackMs: Double, releaseMs: Double): FloatArray {
        val att = exp(-1.0 / (attackMs / 1000 * sampleRate)).toFloat()
        val rel = exp(-1.0 / (releaseMs / 1000 * sampleRate)).toFloat()
        var env = 0f
        return FloatArray(x.size) {
            val a = abs(x[it])
            env = if (a > env) att * env + (1 - att) * a else rel * env + (1 - rel) * a
            env
        }
    }

    fun compressor(x: FloatArray, sampleRate: Int, thresholdDb: Double, ratio: Double, attackMs: Double, releaseMs: Double, makeupDb: Double): FloatArray {
        val env = envelope(x, sampleRate, attackMs, releaseMs)
        val makeup = dbToGain(makeupDb)
        val r = max(1.0, ratio)
        return FloatArray(x.size) {
            val levelDb = 20 * log10(max(1e-6f, env[it]).toDouble())
            val over = levelDb - thresholdDb
            val gainDb = if (over > 0) -over * (1 - 1 / r) else 0.0
            x[it] * dbToGain(gainDb) * makeup
        }
    }

    fun gain(x: FloatArray, db: Double): FloatArray { val g = dbToGain(db); return FloatArray(x.size) { x[it] * g } }

    fun normalize(x: FloatArray, peakDb: Double = -1.0): FloatArray {
        var peak = 0f
        for (s in x) peak = max(peak, abs(s))
        if (peak < 1e-5f) return x.copyOf()
        val g = dbToGain(peakDb) / peak
        return FloatArray(x.size) { x[it] * g }
    }

    /** Soft limiter so heavy effect chains never clip on export. */
    fun limit(x: FloatArray, ceiling: Float = 0.98f): FloatArray {
        var peak = 0f
        for (s in x) peak = max(peak, abs(s))
        if (peak <= ceiling) return x.copyOf()
        return FloatArray(x.size) { (tanh(x[it] / ceiling.toDouble()) * ceiling).toFloat() }
    }

    fun fade(x: FloatArray, sampleRate: Int, inMs: Double, outMs: Double): FloatArray {
        val out = x.copyOf()
        val fi = min(out.size, (inMs / 1000 * sampleRate).toInt())
        val fo = min(out.size, (outMs / 1000 * sampleRate).toInt())
        for (i in 0 until fi) out[i] *= i.toFloat() / fi
        for (i in 0 until fo) out[out.size - 1 - i] *= i.toFloat() / fo
        return out
    }

    /** Removes leading/trailing near-silence, keeping [padMs] of breathing room. */
    fun trimSilence(x: FloatArray, sampleRate: Int, thresholdDb: Double = -50.0, padMs: Double = 80.0): FloatArray {
        val t = dbToGain(thresholdDb)
        val first = x.indexOfFirst { abs(it) > t }
        if (first < 0) return FloatArray(0)
        val last = x.indexOfLast { abs(it) > t }
        val pad = (padMs / 1000 * sampleRate).toInt()
        return x.copyOfRange(max(0, first - pad), min(x.size, last + pad + 1))
    }

    /** Mixes a pitch-shifted, delayed copy of the signal back in (octave layers, doubles, choirs). */
    fun layer(x: FloatArray, sampleRate: Int, semitones: Double, level: Double, delayMs: Double): FloatArray {
        val shifted = pitchShift(x, sampleRate, semitones)
        val d = (delayMs / 1000 * sampleRate).toInt()
        val l = level.toFloat()
        return FloatArray(x.size + d) {
            val a = if (it < x.size) x[it] else 0f
            val b = if (it - d in shifted.indices) shifted[it - d] else 0f
            a + b * l
        }
    }

    /** Drops trailing samples that are effectively silent, but never shorter than [minLength]. */
    fun trimTail(x: FloatArray, minLength: Int): FloatArray {
        var end = x.size
        val t = 0.0005f
        while (end > minLength && abs(x[end - 1]) < t) end--
        return if (end == x.size) x else x.copyOf(end)
    }
}
