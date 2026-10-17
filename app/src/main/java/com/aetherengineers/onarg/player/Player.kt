package com.aetherengineers.onarg.player

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

data class PlaybackState(val key: String? = null, val playing: Boolean = false, val positionMs: Int = 0, val durationMs: Int = 0)

/** Single shared MediaPlayer; [key] identifies what is loaded (preview or a library item). */
class Player {
    private var mp: MediaPlayer? = null
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state

    fun toggle(key: String, file: File) {
        val current = mp
        if (current != null && _state.value.key == key) {
            if (current.isPlaying) current.pause() else current.start()
            tick()
            return
        }
        release()
        _state.value = PlaybackState(key = key)
        mp = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            setDataSource(file.absolutePath)
            setOnCompletionListener { _state.value = _state.value.copy(playing = false, positionMs = 0); seekTo(0) }
            prepare()
            start()
        }
        tick()
    }

    fun seekTo(fraction: Float) {
        mp?.let { it.seekTo((fraction * it.duration).toInt()); tick() }
    }

    /** Refreshes position; the UI calls this periodically while playing. */
    fun tick() {
        val p = mp ?: return
        val key = _state.value.key ?: return
        _state.value = PlaybackState(key, p.isPlaying, p.currentPosition, p.duration)
    }

    fun stop() {
        release()
        _state.value = PlaybackState()
    }

    private fun release() {
        mp?.release()
        mp = null
    }
}
