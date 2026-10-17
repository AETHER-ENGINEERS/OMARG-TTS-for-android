package com.aetherengineers.omarg.audio.dsp

import com.aetherengineers.omarg.audio.AudioBuffer
import com.aetherengineers.omarg.audio.Wav
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class DspTest {
    private val sr = 24000

    private fun tone(freq: Double, seconds: Double = 1.0) =
        FloatArray((sr * seconds).toInt()) { (0.5 * sin(2 * PI * freq * it / sr)).toFloat() }

    /** Dominant frequency estimated from zero crossings. */
    private fun zeroCrossFreq(x: FloatArray): Double {
        var c = 0
        for (i in 1 until x.size) if ((x[i - 1] < 0) != (x[i] < 0)) c++
        return c / 2.0 / (x.size.toDouble() / sr)
    }

    @Test fun timeStretchChangesLengthNotPitch() {
        val x = tone(220.0)
        val slow = Dsp.timeStretch(x, sr, 0.5)
        assertEquals(x.size * 2, slow.size)
        val f = zeroCrossFreq(slow.copyOfRange(sr / 10, slow.size - sr / 10))
        assertTrue("freq $f", abs(f - 220) < 8)
    }

    @Test fun pitchShiftKeepsLengthChangesPitch() {
        val x = tone(220.0)
        val up = Dsp.pitchShift(x, sr, 12.0)
        assertEquals(x.size, up.size)
        val f = zeroCrossFreq(up.copyOfRange(sr / 10, up.size - sr / 5))
        assertTrue("freq $f", abs(f - 440) < 15)
    }

    @Test fun everyPresetProducesFiniteAudio() {
        val x = AudioBuffer(tone(180.0, 0.8), sr)
        for (p in Presets.all) {
            val y = p.process(x)
            assertTrue(p.name, y.size > 0)
            assertTrue(p.name, y.samples.all { it.isFinite() })
            val peak = y.samples.maxOf { abs(it) }
            assertTrue("${p.name} peak $peak", peak < 20f)
        }
    }

    @Test fun chainRoundTrips() {
        for (p in Presets.all) {
            val decoded = EffectChain.decode(p.encode())
            assertNotNull(decoded)
            assertEquals(p.name, decoded!!.name)
            assertEquals(p.steps, decoded.steps)
        }
    }

    @Test fun wavRoundTrip() {
        val x = AudioBuffer(tone(440.0, 0.1), sr)
        val y = Wav.read(Wav.toBytes(x).inputStream())
        assertEquals(sr, y.sampleRate)
        assertEquals(x.size, y.size)
        for (i in x.samples.indices) assertEquals(x.samples[i], y.samples[i], 1e-3f)
    }

    @Test fun reverbAddsTail() {
        val y = Dsp.reverb(tone(300.0, 0.3), sr, 0.8, 0.5, 0.4, 1.0, 20.0)
        assertTrue(y.size > (0.3 * sr).toInt())
    }

    /** "Drunk" must warble pitch at ~10 Hz by roughly ±2 semitones, matching the reference recordings. */
    @Test fun drunkWarblesAtTenHz() {
        val y = Presets.byName("Drunk")!!.process(AudioBuffer(tone(220.0, 2.0), sr)).samples
        // Instantaneous frequency from zero crossings, sampled every 10 ms.
        val crossings = (1 until y.size).filter { (y[it - 1] < 0) != (y[it] < 0) }
        val step = sr / 100
        val freq = (2 until (y.size / step) - 2).map { k ->
            val a = k * step; val b = a + step
            val c = crossings.filter { it in a until b }
            if (c.size < 2) 220.0 else (c.size - 1) / 2.0 / ((c.last() - c.first()).toDouble() / sr)
        }
        val cents = freq.map { 1200 * kotlin.math.ln(it / 220.0) / kotlin.math.ln(2.0) }
        val spread = cents.sorted().let { it[(it.size * 0.95).toInt()] - it[(it.size * 0.05).toInt()] }
        assertTrue("spread $spread", spread in 250.0..500.0)
        // Dominant modulation rate via the best-matching sinusoid between 2 and 16 Hz.
        val best = (20..160).maxBy { r ->
            val f = r / 10.0
            val re = cents.indices.sumOf { cents[it] * kotlin.math.cos(2 * PI * f * it / 100) }
            val im = cents.indices.sumOf { cents[it] * sin(2 * PI * f * it / 100) }
            re * re + im * im
        } / 10.0
        assertTrue("rate $best", best in 9.0..10.6)
    }
}
