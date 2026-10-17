package com.aetherengineers.omarg.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aetherengineers.omarg.player.PlaybackState
import com.aetherengineers.omarg.player.Player
import com.aetherengineers.omarg.tts.VoiceInfo
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                action()
            }
            content()
        }
    }
}

@Composable
fun LabeledSlider(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String,
    steps: Int = 0, onChange: (Float) -> Unit,
) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(display, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, steps = steps)
    }
}

/** Row that looks like a dropdown field and opens a picker. */
@Composable
fun PickerField(label: String, value: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        HorizontalDivider(Modifier.padding(top = 6.dp))
    }
}

@Composable
fun <T> ChoiceDialog(
    title: String, options: List<T>, selected: T?, label: (T) -> String, onDismiss: () -> Unit,
    supporting: (T) -> String? = { null }, onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(options) { o ->
                    val sub = supporting(o)
                    ListItem(
                        modifier = Modifier.clickable { onSelect(o); onDismiss() },
                        leadingContent = { RadioButton(selected = o == selected, onClick = { onSelect(o); onDismiss() }) },
                        headlineContent = { Text(label(o)) },
                        supportingContent = if (sub != null) { { Text(sub) } } else null,
                    )
                }
            }
        },
    )
}

/** Searchable voice list grouped by language; the device language floats to the top. */
@Composable
fun VoicePickerDialog(voices: List<VoiceInfo>, selected: String?, onDismiss: () -> Unit, onSelect: (VoiceInfo) -> Unit) {
    var query by remember { mutableStateOf("") }
    val deviceLang = Locale.getDefault().language
    val filtered = remember(voices, query) {
        voices.filter { query.isBlank() || it.name.contains(query, true) || it.languageLabel.contains(query, true) }
            .sortedWith(compareBy({ it.locale.language != deviceLang }, { it.languageLabel }, { -it.quality }, { it.name }))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Choose a voice") },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text("Search language or voice") }, modifier = Modifier.fillMaxWidth(),
                )
                if (filtered.isEmpty()) Text("No voices. Install voice data in the engine's settings.", Modifier.padding(top = 12.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(filtered, key = { it.name }) { v ->
                        ListItem(
                            modifier = Modifier.clickable { onSelect(v); onDismiss() },
                            leadingContent = { RadioButton(selected = v.name == selected, onClick = { onSelect(v); onDismiss() }) },
                            headlineContent = { Text(v.languageLabel) },
                            supportingContent = {
                                Text(buildString {
                                    append(v.label ?: prettyVoiceName(v.name)); append(" · "); append(v.qualityLabel)
                                    if (v.requiresNetwork) append(" · online")
                                }, maxLines = 2)
                            },
                        )
                    }
                }
            }
        },
    )
}

fun prettyVoiceName(name: String) = name.substringAfterLast('#').replace('_', ' ').replace('-', ' ')

fun formatDuration(seconds: Double): String {
    val s = seconds.toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

/** Play/pause button with seek bar bound to the shared [Player]. */
@Composable
fun PlayerBar(key: String, file: java.io.File, player: Player, state: PlaybackState, onPlay: () -> Unit = { player.toggle(key, file) }) {
    val active = state.key == key
    LaunchedEffect(active, state.playing) {
        while (active && state.playing) { player.tick(); delay(200) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledIconButton(onClick = onPlay) {
            Icon(if (active && state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play")
        }
        Spacer(Modifier.width(8.dp))
        val fraction = if (active && state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
        Slider(value = fraction, onValueChange = { if (active) player.seekTo(it) }, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(
            if (active) "${formatDuration(state.positionMs / 1000.0)} / ${formatDuration(state.durationMs / 1000.0)}" else "",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
