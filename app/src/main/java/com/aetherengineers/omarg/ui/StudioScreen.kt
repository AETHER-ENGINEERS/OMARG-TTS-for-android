package com.aetherengineers.omarg.ui

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aetherengineers.omarg.audio.dsp.EffectChain
import com.aetherengineers.omarg.audio.dsp.Presets
import com.aetherengineers.omarg.data.VoiceSlot
import com.aetherengineers.omarg.player.PlaybackState
import com.aetherengineers.omarg.tts.Script

@Composable
fun StudioScreen(vm: MainViewModel, state: UiState, playback: PlaybackState) {
    var editing by remember { mutableStateOf<EffectChain?>(null) }
    var editingReplaces by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScriptCard(vm, state)
        VoicesCard(vm, state)
        EffectsCard(state, onSelect = vm::selectEffect, onEdit = { chain ->
            editingReplaces = if (chain.builtIn) null else chain.name
            editing = if (chain.builtIn) chain.copy(name = chain.name + " (custom)", builtIn = false, category = "Custom") else chain
        }, onNew = { editingReplaces = null; editing = EffectChain("My effect", emptyList()) })
        MusicCard(vm, state)
        GenerateCard(vm, state, playback)
        Spacer(Modifier.height(24.dp))
    }

    editing?.let { chain ->
        EffectEditorDialog(
            initial = chain,
            busy = state.rendering,
            onPreview = { vm.render(previewChain = it) },
            onDismiss = { editing = null },
            onSave = { vm.saveCustomEffect(it, editingReplaces); editing = null },
            onDelete = if (editingReplaces != null) ({ vm.deleteCustomEffect(editingReplaces!!); editing = null }) else null,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScriptCard(vm: MainViewModel, state: UiState) {
    val context = LocalContext.current
    val openText = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importText) }
    SectionCard("Script", action = {
        Text("${Script.plain(state.text).length} chars", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }) {
        OutlinedTextField(
            value = state.text,
            onValueChange = vm::setText,
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp),
            placeholder = { Text("Type or paste what the narrator should say…\n\nUnlimited length. Use [pause 1s] for pauses and [1]–[4] to switch voices in a dialogue.") },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.let { vm.setText(state.text + it) }
            }, label = { Text("Paste") }, leadingIcon = { Icon(Icons.Filled.ContentPaste, null, Modifier.size(18.dp)) })
            AssistChip(onClick = { openText.launch(arrayOf("text/*")) }, label = { Text("Open .txt") },
                leadingIcon = { Icon(Icons.Filled.FileOpen, null, Modifier.size(18.dp)) })
            AssistChip(onClick = { vm.insertTag("[pause 1s]") }, label = { Text("Pause") },
                leadingIcon = { Icon(Icons.Filled.Timer, null, Modifier.size(18.dp)) })
            if (state.castSize > 1) for (i in 1..state.castSize) {
                AssistChip(onClick = { vm.insertTag("[$i]") }, label = { Text("Voice $i") },
                    leadingIcon = { Icon(Icons.Filled.Person, null, Modifier.size(18.dp)) })
            }
            if (state.text.isNotEmpty()) AssistChip(onClick = { vm.setText("") }, label = { Text("Clear") },
                leadingIcon = { Icon(Icons.Filled.Close, null, Modifier.size(18.dp)) })
        }
    }
}

@Composable
private fun VoicesCard(vm: MainViewModel, state: UiState) {
    SectionCard("Voice", action = {
        TextButton(onClick = { vm.setCastSize(if (state.castSize == 1) 2 else 1) }) {
            Text(if (state.castSize == 1) "Dialogue mode" else "Single voice")
        }
    }) {
        if (!state.ttsReady) Text("Starting voice engine…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (i in 0 until state.castSize) {
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
            if (state.castSize > 1) Text("Voice ${i + 1}  —  tag [${i + 1}]", fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
            SlotEditor(state, state.slots[i], showEffect = state.castSize > 1) { vm.updateSlot(i, it) }
        }
        if (state.castSize in 2..3) TextButton(onClick = { vm.setCastSize(state.castSize + 1) }) { Text("Add voice ${state.castSize + 1}") }
        if (state.castSize > 2) TextButton(onClick = { vm.setCastSize(state.castSize - 1) }) { Text("Remove voice ${state.castSize}") }
    }
}

@Composable
private fun SlotEditor(state: UiState, slot: VoiceSlot, showEffect: Boolean, onChange: (VoiceSlot) -> Unit) {
    var pickEngine by remember { mutableStateOf(false) }
    var pickVoice by remember { mutableStateOf(false) }
    var pickEffect by remember { mutableStateOf(false) }
    val engineKey = state.engineKey(slot)
    val engineLabel = state.engines.firstOrNull { it.packageName == engineKey }?.label ?: "System default"
    val voices = state.voicesFor(slot)
    val voice = voices.firstOrNull { it.name == slot.voice }

    PickerField("Engine", engineLabel) { pickEngine = true }
    PickerField("Voice", voice?.let { "${it.languageLabel} · ${prettyVoiceName(it.name)}" } ?: "Engine default") { pickVoice = true }
    LabeledSlider("Speed", slot.rate, 0.25f..3f, "%.2f×".format(slot.rate)) { onChange(slot.copy(rate = it)) }
    LabeledSlider("Pitch", slot.pitch, 0.25f..2.5f, "%.2f".format(slot.pitch)) { onChange(slot.copy(pitch = it)) }
    if (showEffect) PickerField("Effect for this voice", slot.effect ?: "None") { pickEffect = true }

    if (pickEngine) ChoiceDialog("Text-to-speech engine", state.engines, state.engines.firstOrNull { it.packageName == engineKey },
        label = { it.label }, supporting = { it.packageName }, onDismiss = { pickEngine = false }) {
        onChange(slot.copy(engine = it.packageName))
    }
    if (pickVoice) VoicePickerDialog(voices, slot.voice, onDismiss = { pickVoice = false }) { onChange(slot.copy(voice = it.name)) }
    if (pickEffect) ChoiceDialog("Effect for this voice", listOf<String?>(null) + state.allEffects.map { it.name }, slot.effect,
        label = { it ?: "None" }, onDismiss = { pickEffect = false }) { onChange(slot.copy(effect = it)) }
}

@Composable
private fun EffectsCard(state: UiState, onSelect: (String) -> Unit, onEdit: (EffectChain) -> Unit, onNew: () -> Unit) {
    val categories = remember(state.customEffects) {
        (if (state.customEffects.isNotEmpty()) listOf("Custom") else emptyList()) + Presets.categories
    }
    var category by rememberSaveable { mutableStateOf(state.effectByName(state.effect)?.category ?: Presets.categories.first()) }
    val selected = state.effectByName(state.effect)
    SectionCard("Effect", action = {
        IconButton(onClick = { selected?.let(onEdit) }, enabled = selected != null) { Icon(Icons.Filled.Tune, "Customize effect") }
    }) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { c -> FilterChip(selected = c == category, onClick = { category = c }, label = { Text(c) }) }
        }
        EffectGrid(state.allEffects.filter { it.category == category }, state.effect, onSelect)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(
                selected?.let { if (it.steps.isEmpty()) "${it.name} — no processing" else "${it.name} — " + it.steps.joinToString(" → ") { s -> s.type.label } }
                    ?: state.effect,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
            )
        }
        TextButton(onClick = onNew) { Text("Build a new effect from scratch") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EffectGrid(effects: List<EffectChain>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (e in effects) FilterChip(selected = e.name == selected, onClick = { onSelect(e.name) }, label = { Text(e.name) })
    }
}

@Composable
private fun MusicCard(vm: MainViewModel, state: UiState) {
    val context = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.setMusic(it, displayName(context, it)) }
    }
    SectionCard("Background music", action = {
        if (state.music != null) IconButton(onClick = { vm.updateMusic(null) }) { Icon(Icons.Filled.Delete, "Remove music") }
    }) {
        val bed = state.music
        if (bed == null) {
            OutlinedButton(onClick = { pick.launch(arrayOf("audio/*")) }) {
                Icon(Icons.Filled.MusicNote, null); Spacer(Modifier.width(8.dp)); Text("Add music from your files")
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(bed.name, modifier = Modifier.weight(1f), maxLines = 1)
                IconButton(onClick = { pick.launch(arrayOf("audio/*")) }) { Icon(Icons.Filled.Edit, "Change music") }
            }
            LabeledSlider("Music volume", bed.volume, 0f..1f, "${(bed.volume * 100).toInt()}%") { vm.updateMusic(bed.copy(volume = it)) }
            SwitchRow("Lower music while speaking (ducking)", bed.duck) { vm.updateMusic(bed.copy(duck = it)) }
            SwitchRow("Loop music", bed.loop) { vm.updateMusic(bed.copy(loop = it)) }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun GenerateCard(vm: MainViewModel, state: UiState, playback: PlaybackState) {
    SectionCard("Generate") {
        if (state.rendering) {
            Text(state.progressLabel, style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = vm::cancelRender) { Text("Cancel") }
        } else {
            Button(onClick = { vm.render() }, enabled = state.ttsReady && state.text.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.GraphicEq, null); Spacer(Modifier.width(8.dp)); Text("Generate narration")
            }
        }
        state.latest?.let { rec ->
            HorizontalDivider()
            Text(rec.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("${formatDuration(rec.durationSeconds)} · ${rec.effect} · saved to Library", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            PlayerBar(rec.id, rec.file, vm.player, playback)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.share(rec) }) { Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text("Share") }
                OutlinedButton(onClick = { vm.saveToDevice(rec) }) { Icon(Icons.Filled.SaveAlt, null); Spacer(Modifier.width(6.dp)); Text("Save ${state.exportFormat.extension.uppercase()}") }
            }
        }
    }
}

private fun displayName(context: Context, uri: Uri): String =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "Music"
