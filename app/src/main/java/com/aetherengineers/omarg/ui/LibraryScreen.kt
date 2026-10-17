package com.aetherengineers.omarg.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aetherengineers.omarg.data.Recording
import com.aetherengineers.omarg.player.PlaybackState
import java.text.DateFormat
import java.util.Date

@Composable
fun LibraryScreen(vm: MainViewModel, state: UiState, playback: PlaybackState, openStudio: () -> Unit) {
    var renaming by remember { mutableStateOf<Recording?>(null) }
    var deleting by remember { mutableStateOf<Recording?>(null) }

    if (state.recordings.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Your narrations will appear here.\nEverything stays on this phone.", textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.recordings, key = { it.id }) { rec ->
                RecordingCard(rec, vm, playback,
                    onRename = { renaming = rec }, onDelete = { deleting = rec },
                    onLoad = { vm.loadIntoStudio(rec); openStudio() })
            }
        }
    }

    renaming?.let { rec ->
        var title by remember(rec.id) { mutableStateOf(rec.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(title, { title = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.rename(rec, title.ifBlank { rec.title }); renaming = null }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { rec ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${rec.title}\"?") },
            text = { Text("This removes the recording from the app. Copies you already shared or saved are not affected.") },
            confirmButton = { TextButton(onClick = { vm.delete(rec); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RecordingCard(rec: Recording, vm: MainViewModel, playback: PlaybackState, onRename: () -> Unit, onDelete: () -> Unit, onLoad: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(rec.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        "${formatDuration(rec.durationSeconds)} · ${rec.effect} · " +
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(rec.createdAt)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; vm.share(rec) })
                        DropdownMenuItem(text = { Text("Save to Music folder") }, onClick = { menu = false; vm.saveToDevice(rec) })
                        DropdownMenuItem(text = { Text("Edit text in Studio") }, onClick = { menu = false; onLoad() })
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            PlayerBar(rec.id, rec.file, vm.player, playback, onPlay = { vm.togglePlay(rec) })
        }
    }
}
