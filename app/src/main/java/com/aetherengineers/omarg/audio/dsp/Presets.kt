package com.aetherengineers.omarg.audio.dsp

import com.aetherengineers.omarg.audio.dsp.EffectType.BITCRUSH
import com.aetherengineers.omarg.audio.dsp.EffectType.CHORUS
import com.aetherengineers.omarg.audio.dsp.EffectType.COMPRESSOR
import com.aetherengineers.omarg.audio.dsp.EffectType.DISTORTION
import com.aetherengineers.omarg.audio.dsp.EffectType.ECHO
import com.aetherengineers.omarg.audio.dsp.EffectType.EQ
import com.aetherengineers.omarg.audio.dsp.EffectType.FILTER
import com.aetherengineers.omarg.audio.dsp.EffectType.FLANGER
import com.aetherengineers.omarg.audio.dsp.EffectType.LAYER
import com.aetherengineers.omarg.audio.dsp.EffectType.METALLIC
import com.aetherengineers.omarg.audio.dsp.EffectType.NOISE
import com.aetherengineers.omarg.audio.dsp.EffectType.PITCH
import com.aetherengineers.omarg.audio.dsp.EffectType.REVERB
import com.aetherengineers.omarg.audio.dsp.EffectType.REVERSE
import com.aetherengineers.omarg.audio.dsp.EffectType.RING_MOD
import com.aetherengineers.omarg.audio.dsp.EffectType.SPEED
import com.aetherengineers.omarg.audio.dsp.EffectType.TEMPO
import com.aetherengineers.omarg.audio.dsp.EffectType.TREMOLO
import com.aetherengineers.omarg.audio.dsp.EffectType.VIBRATO

/** Built-in voice effects. Every one of them is free and editable. */
object Presets {

    private fun s(type: EffectType, vararg p: Pair<String, Double>) = EffectStep(type, type.defaults() + p.toMap())
    private fun preset(category: String, name: String, vararg steps: EffectStep) = EffectChain(name, steps.toList(), category, builtIn = true)

    const val NATURAL = "Natural"

    val all: List<EffectChain> = listOf(
        preset("Narration", NATURAL),
        preset("Narration", "Movie trailer",
            s(PITCH, "semitones" to -3.0), s(EQ, "bass" to 7.0, "mid" to -1.0, "treble" to 2.0),
            s(COMPRESSOR, "threshold" to -24.0, "ratio" to 5.0, "makeup" to 8.0),
            s(REVERB, "room" to 0.45, "wet" to 0.12, "predelay" to 30.0)),
        preset("Narration", "Documentary",
            s(PITCH, "semitones" to -1.5), s(EQ, "bass" to 4.0, "treble" to 1.5),
            s(COMPRESSOR, "threshold" to -22.0, "ratio" to 3.0, "makeup" to 5.0),
            s(REVERB, "room" to 0.3, "wet" to 0.07)),
        preset("Narration", "Radio host",
            s(EQ, "bass" to 5.0, "mid" to 2.0, "treble" to 3.0),
            s(COMPRESSOR, "threshold" to -28.0, "ratio" to 6.0, "makeup" to 10.0)),
        preset("Narration", "Storyteller",
            s(TEMPO, "tempo" to 0.92), s(EQ, "bass" to 3.0, "treble" to -1.0),
            s(REVERB, "room" to 0.55, "wet" to 0.15)),
        preset("Narration", "Deep voice", s(PITCH, "semitones" to -5.0), s(EQ, "bass" to 4.0)),
        preset("Narration", "Stadium announcer",
            s(COMPRESSOR, "threshold" to -26.0, "ratio" to 6.0, "makeup" to 9.0),
            s(ECHO, "delay" to 380.0, "feedback" to 0.3, "mix" to 0.35),
            s(REVERB, "room" to 0.85, "wet" to 0.3, "predelay" to 60.0)),

        preset("Characters", "Chipmunk", s(PITCH, "semitones" to 9.0), s(TEMPO, "tempo" to 1.08)),
        preset("Characters", "Helium", s(PITCH, "semitones" to 5.0), s(EQ, "bass" to -4.0, "treble" to 3.0)),
        preset("Characters", "Child", s(PITCH, "semitones" to 4.0), s(EQ, "bass" to -6.0, "treble" to 2.0), s(TEMPO, "tempo" to 1.05)),
        preset("Characters", "Giant", s(PITCH, "semitones" to -8.0), s(TEMPO, "tempo" to 0.88),
            s(REVERB, "room" to 0.9, "wet" to 0.3, "predelay" to 50.0)),
        preset("Characters", "Old radio",
            s(FILTER, "low" to 500.0, "high" to 3000.0), s(DISTORTION, "drive" to 3.0, "mix" to 0.6),
            s(NOISE, "level" to -34.0, "low" to 800.0, "high" to 5000.0), s(TREMOLO, "rate" to 0.7, "depth" to 0.12)),
        // Measured from reference clips: a fast ~9.8 Hz pitch warble of about ±2 semitones, no other processing.
        preset("Characters", "Drunk", s(VIBRATO, "depth" to 4.0, "rate" to 9.8)),
        preset("Characters", "Tipsy", s(VIBRATO, "depth" to 2.2, "rate" to 9.8)),
        preset("Characters", "Wasted", s(TEMPO, "tempo" to 0.88), s(PITCH, "semitones" to -1.0),
            s(VIBRATO, "depth" to 5.0, "rate" to 9.8), s(VIBRATO, "depth" to 8.0, "rate" to 0.6)),
        preset("Characters", "Woozy", s(TEMPO, "tempo" to 0.82), s(VIBRATO, "depth" to 9.0, "rate" to 0.6)),
        preset("Characters", "Fast talker", s(TEMPO, "tempo" to 1.6)),
        preset("Characters", "Slow motion", s(SPEED, "rate" to 0.7)),
        preset("Characters", "Fan", s(TREMOLO, "rate" to 17.0, "depth" to 0.8)),
        preset("Characters", "Nervous", s(VIBRATO, "depth" to 0.8, "rate" to 9.0), s(TREMOLO, "rate" to 9.0, "depth" to 0.25)),

        preset("Sci-fi", "Robot",
            s(RING_MOD, "freq" to 55.0, "mix" to 0.9), s(METALLIC, "delay" to 6.0, "feedback" to 0.65, "mix" to 0.5),
            s(DISTORTION, "drive" to 1.8, "mix" to 0.5)),
        preset("Sci-fi", "Cyborg",
            s(PITCH, "semitones" to -2.0), s(FLANGER, "depth" to 1.5, "rate" to 0.4, "feedback" to 0.8, "mix" to 0.6),
            s(RING_MOD, "freq" to 140.0, "mix" to 0.3)),
        preset("Sci-fi", "Alien",
            s(PITCH, "semitones" to 3.0), s(RING_MOD, "freq" to 320.0, "mix" to 0.4),
            s(FLANGER, "depth" to 4.0, "rate" to 0.8, "feedback" to 0.5, "mix" to 0.5)),
        preset("Sci-fi", "Space station",
            s(FILTER, "low" to 250.0, "high" to 6000.0), s(FLANGER, "depth" to 2.0, "rate" to 0.15, "feedback" to 0.4, "mix" to 0.4),
            s(REVERB, "room" to 0.8, "wet" to 0.35)),
        preset("Sci-fi", "8-bit",
            s(PITCH, "semitones" to 2.0), s(BITCRUSH, "bits" to 5.0, "rate" to 7000.0), s(FILTER, "low" to 150.0, "high" to 7000.0)),
        preset("Sci-fi", "Hologram",
            s(CHORUS, "depth" to 3.0, "rate" to 3.0, "voices" to 2.0, "mix" to 0.6), s(BITCRUSH, "bits" to 10.0, "rate" to 16000.0),
            s(ECHO, "delay" to 90.0, "feedback" to 0.2, "mix" to 0.3)),

        preset("Horror", "Monster",
            s(PITCH, "semitones" to -9.0), s(DISTORTION, "drive" to 3.0, "mix" to 0.6),
            s(REVERB, "room" to 0.6, "wet" to 0.2)),
        preset("Horror", "Demon",
            s(LAYER, "semitones" to -12.0, "level" to 0.8), s(PITCH, "semitones" to -4.0),
            s(DISTORTION, "drive" to 2.5, "mix" to 0.5), s(REVERB, "room" to 0.8, "wet" to 0.3)),
        preset("Horror", "Ghost",
            s(PITCH, "semitones" to 2.0), s(REVERSE), s(REVERB, "room" to 0.9, "wet" to 0.7, "dry" to 0.8), s(REVERSE),
            s(TREMOLO, "rate" to 0.9, "depth" to 0.3), s(ECHO, "delay" to 450.0, "feedback" to 0.35, "mix" to 0.3)),
        preset("Horror", "Possessed",
            s(LAYER, "semitones" to -12.0, "level" to 0.7), s(LAYER, "semitones" to 7.0, "level" to 0.25, "delay" to 30.0),
            s(VIBRATO, "depth" to 1.5, "rate" to 4.0), s(REVERB, "room" to 0.7, "wet" to 0.25)),
        preset("Horror", "Nightmare",
            s(PITCH, "semitones" to -6.0), s(REVERSE), s(REVERB, "room" to 0.95, "wet" to 0.8, "dry" to 0.6), s(REVERSE),
            s(FLANGER, "depth" to 5.0, "rate" to 0.1, "feedback" to 0.6, "mix" to 0.4)),

        preset("Places", "Echo", s(ECHO, "delay" to 280.0, "feedback" to 0.45, "mix" to 0.5)),
        preset("Places", "Cave", s(ECHO, "delay" to 170.0, "feedback" to 0.35, "mix" to 0.4), s(REVERB, "room" to 0.88, "wet" to 0.4)),
        preset("Places", "Cathedral", s(REVERB, "room" to 0.97, "damping" to 0.3, "wet" to 0.45, "predelay" to 45.0)),
        preset("Places", "Small room", s(REVERB, "room" to 0.25, "damping" to 0.6, "wet" to 0.18, "predelay" to 5.0)),
        preset("Places", "Bathroom", s(REVERB, "room" to 0.55, "damping" to 0.05, "wet" to 0.35, "predelay" to 3.0), s(EQ, "mid" to 3.0)),
        preset("Places", "Underwater",
            s(FILTER, "low" to 60.0, "high" to 550.0), s(VIBRATO, "depth" to 3.0, "rate" to 1.2),
            s(REVERB, "room" to 0.7, "damping" to 0.8, "wet" to 0.35)),

        preset("Devices", "Telephone", s(FILTER, "low" to 400.0, "high" to 3200.0), s(DISTORTION, "drive" to 1.5, "mix" to 0.5),
            s(BITCRUSH, "bits" to 12.0, "rate" to 8000.0)),
        preset("Devices", "Walkie-talkie", s(FILTER, "low" to 600.0, "high" to 2600.0), s(DISTORTION, "drive" to 6.0, "mix" to 0.8),
            s(NOISE, "level" to -30.0, "low" to 1000.0, "high" to 4000.0), s(BITCRUSH, "bits" to 8.0, "rate" to 8000.0)),
        preset("Devices", "Megaphone", s(FILTER, "low" to 700.0, "high" to 4200.0), s(EQ, "mid" to 8.0),
            s(DISTORTION, "drive" to 3.0, "mix" to 0.7)),
        preset("Devices", "Intercom", s(FILTER, "low" to 300.0, "high" to 3500.0), s(COMPRESSOR, "threshold" to -30.0, "ratio" to 8.0, "makeup" to 10.0),
            s(REVERB, "room" to 0.4, "wet" to 0.15)),

        preset("Music", "Choir",
            s(LAYER, "semitones" to 12.0, "level" to 0.3, "delay" to 15.0), s(LAYER, "semitones" to -12.0, "level" to 0.35, "delay" to 25.0),
            s(CHORUS, "voices" to 4.0, "depth" to 8.0, "mix" to 0.6), s(REVERB, "room" to 0.9, "wet" to 0.4)),
        preset("Music", "Crowd", s(CHORUS, "voices" to 6.0, "depth" to 18.0, "rate" to 0.3, "mix" to 0.8), s(REVERB, "room" to 0.5, "wet" to 0.2)),
        preset("Music", "Harmony", s(LAYER, "semitones" to 4.0, "level" to 0.5), s(LAYER, "semitones" to 7.0, "level" to 0.4),
            s(REVERB, "room" to 0.6, "wet" to 0.2)),
        preset("Music", "Double track", s(CHORUS, "voices" to 1.0, "depth" to 4.0, "rate" to 0.2, "mix" to 0.7)),
    )

    val categories: List<String> = all.map { it.category }.distinct()

    fun byName(name: String): EffectChain? = all.firstOrNull { it.name == name }
}
