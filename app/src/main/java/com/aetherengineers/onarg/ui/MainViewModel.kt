package com.aetherengineers.onarg.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aetherengineers.onarg.audio.dsp.EffectChain
import com.aetherengineers.onarg.audio.dsp.Presets
import com.aetherengineers.onarg.data.ExportFormat
import com.aetherengineers.onarg.data.Library
import com.aetherengineers.onarg.data.MusicBed
import com.aetherengineers.onarg.data.Prefs
import com.aetherengineers.onarg.data.Recording
import com.aetherengineers.onarg.data.VoiceSlot
import com.aetherengineers.onarg.player.Player
import com.aetherengineers.onarg.render.RenderRequest
import com.aetherengineers.onarg.render.Renderer
import com.aetherengineers.onarg.tts.EngineInfo
import com.aetherengineers.onarg.tts.Script
import com.aetherengineers.onarg.tts.TtsEngine
import com.aetherengineers.onarg.tts.VoiceInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class UiState(
    val text: String = "",
    val engines: List<EngineInfo> = emptyList(),
    val defaultEngine: String? = null,
    val voices: Map<String, List<VoiceInfo>> = emptyMap(),
    val slots: List<VoiceSlot> = List(4) { VoiceSlot() },
    val castSize: Int = 1,
    val effect: String = Presets.NATURAL,
    val customEffects: List<EffectChain> = emptyList(),
    val music: MusicBed? = null,
    val rendering: Boolean = false,
    val progressLabel: String = "",
    val progress: Float = 0f,
    val latest: Recording? = null,
    val recordings: List<Recording> = emptyList(),
    val exportFormat: ExportFormat = ExportFormat.M4A,
    val allowNetworkVoices: Boolean = false,
    val trimSilence: Boolean = true,
    val normalize: Boolean = true,
    val sentenceGapMs: Int = 250,
    val ttsReady: Boolean = false,
) {
    val allEffects: List<EffectChain> get() = Presets.all + customEffects
    fun effectByName(name: String?): EffectChain? = name?.let { n -> customEffects.firstOrNull { it.name == n } ?: Presets.byName(n) }
    fun engineKey(slot: VoiceSlot) = slot.engine ?: defaultEngine ?: ""
    fun voicesFor(slot: VoiceSlot): List<VoiceInfo> =
        voices[engineKey(slot)].orEmpty().filter { it.installed && (allowNetworkVoices || !it.requiresNetwork) }
}

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data class Launch(val intent: Intent) : UiEvent
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val library = Library(app)
    val player = Player()

    private val _state = MutableStateFlow(
        UiState(
            text = prefs.text, slots = prefs.slots, castSize = prefs.castSize, effect = prefs.effect,
            customEffects = prefs.customEffects, music = prefs.music, exportFormat = prefs.exportFormat,
            allowNetworkVoices = prefs.allowNetworkVoices, trimSilence = prefs.trimSilence,
            normalize = prefs.normalize, sentenceGapMs = prefs.sentenceGapMs,
        )
    )
    val state: StateFlow<UiState> = _state

    private val events = Channel<UiEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private val engines = mutableMapOf<String, TtsEngine>()
    private val engineLock = Mutex()
    private var renderJob: Job? = null
    private val renderer = Renderer(app) { pkg -> engine(pkg) }

    init {
        viewModelScope.launch {
            val default = engine(null)
            if (default == null) {
                message("No text-to-speech engine found. Install Samsung TTS, Google Speech Services or SherpaTTS.")
            } else {
                _state.update { it.copy(engines = default.engines, defaultEngine = default.packageName, ttsReady = true) }
                loadVoices(default)
                _state.value.slots.mapNotNull { it.engine }.distinct().forEach { pkg -> engine(pkg)?.let { loadVoices(it) } }
                // First run: preselect the engine's default voice.
                val first = _state.value.slots[0]
                if (first.voice == null) default.defaultVoiceName()?.let { updateSlot(0, first.copy(voice = it)) }
            }
            refreshLibrary()
        }
    }

    private suspend fun engine(pkg: String?): TtsEngine? = engineLock.withLock {
        val key = pkg ?: _state.value.defaultEngine
        key?.let { engines[it] }?.let { return@withLock it }
        val created = TtsEngine.create(getApplication(), pkg) ?: return@withLock null
        engines[created.packageName] = created
        created
    }

    private fun loadVoices(engine: TtsEngine) {
        _state.update { it.copy(voices = it.voices + (engine.packageName to engine.voices())) }
    }

    private fun message(text: String) { events.trySend(UiEvent.Message(text)) }

    private fun refreshLibrary() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { library.list() }
            _state.update { s -> s.copy(recordings = list, latest = s.latest?.let { l -> list.firstOrNull { it.id == l.id } }) }
        }
    }

    // ------------------------------------------------------------------ editing

    fun setText(text: String) { _state.update { it.copy(text = text) }; prefs.text = text }

    fun insertTag(tag: String) = setText(_state.value.text.trimEnd() + " " + tag + " ")

    fun updateSlot(index: Int, slot: VoiceSlot) {
        val old = _state.value.slots[index]
        val fixed = if (slot.engine != old.engine) slot.copy(voice = null) else slot
        _state.update { it.copy(slots = it.slots.toMutableList().also { l -> l[index] = fixed }) }
        prefs.slots = _state.value.slots
        if (fixed.engine != old.engine) viewModelScope.launch {
            engine(fixed.engine)?.let { e ->
                loadVoices(e)
                if (fixed.voice == null) e.defaultVoiceName()?.let { v -> updateSlot(index, _state.value.slots[index].copy(voice = v)) }
            } ?: message("That engine could not be started.")
        }
    }

    fun setCastSize(n: Int) { _state.update { it.copy(castSize = n.coerceIn(1, 4)) }; prefs.castSize = _state.value.castSize }

    fun selectEffect(name: String) { _state.update { it.copy(effect = name) }; prefs.effect = name }

    fun saveCustomEffect(chain: EffectChain, replacing: String?) {
        var name = chain.name.trim().ifEmpty { "My effect" }
        val others = _state.value.customEffects.filter { it.name != replacing }
        val taken = others.map { it.name }.toSet() + Presets.all.map { it.name }
        var n = 2
        val base = name
        while (name in taken) name = "$base ${n++}"
        val saved = chain.copy(name = name, category = "Custom", builtIn = false)
        val list = others + saved
        _state.update { it.copy(customEffects = list) }
        prefs.customEffects = list
        selectEffect(name)
    }

    fun deleteCustomEffect(name: String) {
        val list = _state.value.customEffects.filter { it.name != name }
        _state.update { it.copy(customEffects = list) }
        prefs.customEffects = list
        if (_state.value.effect == name) selectEffect(Presets.NATURAL)
    }

    fun setMusic(uri: Uri, name: String) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        updateMusic(MusicBed(uri.toString(), name))
    }

    fun updateMusic(bed: MusicBed?) { _state.update { it.copy(music = bed) }; prefs.music = bed }

    fun setExportFormat(f: ExportFormat) { _state.update { it.copy(exportFormat = f) }; prefs.exportFormat = f }
    fun setAllowNetworkVoices(v: Boolean) { _state.update { it.copy(allowNetworkVoices = v) }; prefs.allowNetworkVoices = v }
    fun setTrimSilence(v: Boolean) { _state.update { it.copy(trimSilence = v) }; prefs.trimSilence = v }
    fun setNormalize(v: Boolean) { _state.update { it.copy(normalize = v) }; prefs.normalize = v }
    fun setSentenceGap(ms: Int) { _state.update { it.copy(sentenceGapMs = ms) }; prefs.sentenceGapMs = ms }

    fun importText(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            if (text == null) message("Could not read that file") else setText(text)
        }
    }

    // ------------------------------------------------------------------ rendering

    /** Renders the script. With [previewChain], only the opening of the text is rendered with that effect, and not saved. */
    fun render(previewChain: EffectChain? = null) {
        if (_state.value.rendering) return
        val s = _state.value
        player.stop()
        renderJob = viewModelScope.launch {
            _state.update { it.copy(rendering = true, progress = 0f, progressLabel = "Starting voice engine") }
            try {
                val cast = s.slots.take(s.castSize)
                val text = if (previewChain != null) Script.chunk(Script.plain(s.text).ifEmpty { "This is a preview of your voice effect." }, 220).first() else s.text
                val request = RenderRequest(
                    text = text,
                    slots = cast,
                    masterEffect = previewChain ?: s.effectByName(s.effect),
                    slotEffects = if (previewChain != null) emptyMap() else
                        cast.withIndex().mapNotNull { (i, slot) -> s.effectByName(slot.effect)?.let { i to it } }.toMap(),
                    music = if (previewChain != null) null else s.music,
                    trimSilence = s.trimSilence,
                    normalize = s.normalize,
                    sentenceGapMs = s.sentenceGapMs,
                )
                val audio = renderer.render(request) { label, p -> _state.update { it.copy(progressLabel = label, progress = p) } }
                if (previewChain != null) {
                    val file = withContext(Dispatchers.IO) {
                        java.io.File(getApplication<Application>().cacheDir, "preview.wav").also { com.aetherengineers.onarg.audio.Wav.write(audio, it) }
                    }
                    player.toggle("preview-" + System.nanoTime(), file)
                } else {
                    val title = Script.plain(s.text).take(40).ifEmpty { "Narration" }
                    val rec = withContext(Dispatchers.IO) { library.save(audio, title, s.text, s.effect) }
                    _state.update { it.copy(latest = rec) }
                    refreshLibrary()
                    player.toggle(rec.id, rec.file)
                }
            } catch (e: CancellationException) {
                engines.values.forEach { it.stop() }
                throw e
            } catch (e: Throwable) {
                message(e.message ?: "Something went wrong while rendering")
            } finally {
                _state.update { it.copy(rendering = false, progress = 0f, progressLabel = "") }
            }
        }
    }

    fun cancelRender() { renderJob?.cancel() }

    // ------------------------------------------------------------------ library

    fun togglePlay(rec: Recording) = runCatching { player.toggle(rec.id, rec.file) }.onFailure { message("Cannot play this file") }

    fun share(rec: Recording) {
        viewModelScope.launch {
            val format = _state.value.exportFormat
            runCatching {
                val file = withContext(Dispatchers.Default) { library.exportFile(rec, format) }
                val uri = library.shareUri(file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = format.mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                events.send(UiEvent.Launch(Intent.createChooser(send, "Share narration")))
            }.onFailure { message("Export failed: ${it.message}") }
        }
    }

    fun saveToDevice(rec: Recording) {
        viewModelScope.launch {
            val format = _state.value.exportFormat
            runCatching {
                withContext(Dispatchers.Default) { library.saveToDevice(library.exportFile(rec, format), format) }
            }.onSuccess { message("Saved to $it") }.onFailure { message("Save failed: ${it.message}") }
        }
    }

    fun rename(rec: Recording, title: String) {
        viewModelScope.launch { withContext(Dispatchers.IO) { library.rename(rec, title) }; refreshLibrary() }
    }

    fun delete(rec: Recording) {
        if (player.state.value.key == rec.id) player.stop()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { library.delete(rec) }
            if (_state.value.latest?.id == rec.id) _state.update { it.copy(latest = null) }
            refreshLibrary()
        }
    }

    fun loadIntoStudio(rec: Recording) {
        setText(rec.text)
        if (_state.value.effectByName(rec.effect) != null) selectEffect(rec.effect)
    }

    override fun onCleared() {
        player.stop()
        engines.values.forEach { it.shutdown() }
        super.onCleared()
    }
}
