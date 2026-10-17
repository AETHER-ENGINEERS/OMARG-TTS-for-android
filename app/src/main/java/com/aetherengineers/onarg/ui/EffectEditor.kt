package com.aetherengineers.onarg.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aetherengineers.onarg.audio.dsp.EffectChain
import com.aetherengineers.onarg.audio.dsp.EffectStep
import com.aetherengineers.onarg.audio.dsp.EffectType
import com.aetherengineers.onarg.audio.dsp.ParamSpec
import kotlin.math.roundToInt

/** Full-screen editor for an effect chain: add, reorder, toggle and tune each processing step. */
@Composable
fun EffectEditorDialog(
    initial: EffectChain,
    busy: Boolean,
    onPreview: (EffectChain) -> Unit,
    onDismiss: () -> Unit,
    onSave: (EffectChain) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial.name) }
    var steps by remember { mutableStateOf(initial.steps) }
    var addMenu by remember { mutableStateOf(false) }
    val chain = EffectChain(name, steps)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close") }
                    Text("Effect editor", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete effect") }
                    TextButton(onClick = { onSave(chain) }) { Text("Save") }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        Button(onClick = { addMenu = true }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Add step") }
                        DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                            EffectType.entries.forEach { t ->
                                DropdownMenuItem(text = { Text(t.label) }, onClick = { steps = steps + EffectStep(t); addMenu = false })
                            }
                        }
                    }
                    OutlinedButton(onClick = { onPreview(chain) }, enabled = !busy) {
                        Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(if (busy) "Rendering…" else "Preview")
                    }
                }
                if (steps.isEmpty()) Text("No steps yet. Add pitch, reverb, echo, robot and more — they run top to bottom.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(steps) { index, step ->
                        StepCard(
                            step = step,
                            canUp = index > 0, canDown = index < steps.lastIndex,
                            onChange = { steps = steps.toMutableList().also { l -> l[index] = it } },
                            onMove = { d -> steps = steps.toMutableList().also { l -> val s = l.removeAt(index); l.add(index + d, s) } },
                            onRemove = { steps = steps.toMutableList().also { l -> l.removeAt(index) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(step: EffectStep, canUp: Boolean, canDown: Boolean, onChange: (EffectStep) -> Unit, onMove: (Int) -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(step.type.label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { onMove(-1) }, enabled = canUp) { Icon(Icons.Filled.ArrowUpward, "Move up") }
                IconButton(onClick = { onMove(1) }, enabled = canDown) { Icon(Icons.Filled.ArrowDownward, "Move down") }
                Switch(checked = step.enabled, onCheckedChange = { onChange(step.copy(enabled = it)) })
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, "Remove step") }
            }
            if (step.enabled) for (p in step.type.params) ParamSlider(p, step[p.key]) { v -> onChange(step.copy(params = step.params + (p.key to v))) }
        }
    }
}

@Composable
private fun ParamSlider(spec: ParamSpec, value: Double, onChange: (Double) -> Unit) {
    val steps = if (spec.step > 0) ((spec.max - spec.min) / spec.step).roundToInt() - 1 else 0
    val shown = if (spec.max - spec.min >= 100) "%.0f".format(value) else "%.2f".format(value)
    LabeledSlider(spec.label, value.toFloat(), spec.min.toFloat()..spec.max.toFloat(), "$shown ${spec.unit}".trim(), steps.coerceAtLeast(0)) {
        val v = if (spec.step > 0) (Math.round(it / spec.step) * spec.step) else it.toDouble()
        onChange(v.coerceIn(spec.min, spec.max))
    }
}
