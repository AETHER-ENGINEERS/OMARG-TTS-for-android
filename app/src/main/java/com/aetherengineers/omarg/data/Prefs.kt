package com.aetherengineers.omarg.data

import android.content.Context
import androidx.core.content.edit
import com.aetherengineers.omarg.audio.dsp.EffectChain
import org.json.JSONArray
import org.json.JSONObject

data class VoiceSlot(
    val engine: String? = null,
    val voice: String? = null,
    val rate: Float = 1f,
    val pitch: Float = 1f,
    /** Name of an effect applied only to this voice (dialogue mode), or null. */
    val effect: String? = null,
)

data class MusicBed(val uri: String, val name: String, val volume: Float = 0.25f, val duck: Boolean = true, val loop: Boolean = true)

enum class ExportFormat(val extension: String, val mime: String, val label: String) {
    M4A("m4a", "audio/mp4", "M4A (AAC) — small, plays everywhere"),
    WAV("wav", "audio/wav", "WAV — lossless, larger"),
}

/** All app state lives on-device in private SharedPreferences. Nothing is ever sent anywhere. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("omarg", Context.MODE_PRIVATE)

    var text: String
        get() = sp.getString("text", null) ?: ""
        set(v) = sp.edit { putString("text", v) }

    var slots: List<VoiceSlot>
        get() = runCatching {
            val arr = JSONArray(sp.getString("slots", "[]"))
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                VoiceSlot(
                    engine = o.optString("engine").ifEmpty { null },
                    voice = o.optString("voice").ifEmpty { null },
                    rate = o.optDouble("rate", 1.0).toFloat(),
                    pitch = o.optDouble("pitch", 1.0).toFloat(),
                    effect = o.optString("effect").ifEmpty { null },
                )
            }
        }.getOrDefault(emptyList()).let { saved -> List(4) { saved.getOrElse(it) { VoiceSlot() } } }
        set(v) = sp.edit {
            putString("slots", JSONArray(v.map {
                JSONObject().put("engine", it.engine ?: "").put("voice", it.voice ?: "")
                    .put("rate", it.rate.toDouble()).put("pitch", it.pitch.toDouble()).put("effect", it.effect ?: "")
            }).toString())
        }

    var castSize: Int
        get() = sp.getInt("castSize", 1)
        set(v) = sp.edit { putInt("castSize", v) }

    var effect: String
        get() = sp.getString("effect", null) ?: "Natural"
        set(v) = sp.edit { putString("effect", v) }

    var customEffects: List<EffectChain>
        get() = runCatching {
            val arr = JSONArray(sp.getString("custom", "[]"))
            List(arr.length()) { EffectChain.decode(arr.getString(it)) }.filterNotNull()
        }.getOrDefault(emptyList())
        set(v) = sp.edit { putString("custom", JSONArray(v.map { it.encode() }).toString()) }

    var music: MusicBed?
        get() = runCatching {
            val o = JSONObject(sp.getString("music", null) ?: return null)
            MusicBed(o.getString("uri"), o.getString("name"), o.optDouble("volume", 0.25).toFloat(),
                o.optBoolean("duck", true), o.optBoolean("loop", true))
        }.getOrNull()
        set(v) = sp.edit {
            if (v == null) remove("music") else putString("music", JSONObject().put("uri", v.uri).put("name", v.name)
                .put("volume", v.volume.toDouble()).put("duck", v.duck).put("loop", v.loop).toString())
        }

    var exportFormat: ExportFormat
        get() = runCatching { ExportFormat.valueOf(sp.getString("format", null)!!) }.getOrDefault(ExportFormat.M4A)
        set(v) = sp.edit { putString("format", v.name) }

    var allowNetworkVoices: Boolean
        get() = sp.getBoolean("networkVoices", false)
        set(v) = sp.edit { putBoolean("networkVoices", v) }

    var trimSilence: Boolean
        get() = sp.getBoolean("trim", true)
        set(v) = sp.edit { putBoolean("trim", v) }

    var normalize: Boolean
        get() = sp.getBoolean("normalize", true)
        set(v) = sp.edit { putBoolean("normalize", v) }

    var sentenceGapMs: Int
        get() = sp.getInt("gap", 250)
        set(v) = sp.edit { putInt("gap", v) }
}
