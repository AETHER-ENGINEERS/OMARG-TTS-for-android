package com.aetherengineers.onarg.audio.dsp

import com.aetherengineers.onarg.audio.AudioBuffer
import com.aetherengineers.onarg.audio.Wav
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
}
