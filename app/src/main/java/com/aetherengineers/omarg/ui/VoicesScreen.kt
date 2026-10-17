package com.aetherengineers.omarg.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.aetherengineers.omarg.tts.neural.CatalogEntry
import com.aetherengineers.omarg.tts.neural.VoiceCatalog

/** Manage the built-in neural engine's voice packs: browse, download (via the browser), import, delete. */
@Composable
fun VoicesScreen(vm: MainViewModel, state: UiState) {
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importPack) }
    var deleting by remember { mutableStateOf<InstalledPack?>(null) }
    var group by rememberSaveable { mutableStateOf(VoiceCatalog.groups.first()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard("Built-in neural voices") {
            Text(
                "OMARG includes its own offline neural speech engine (sherpa-onnx). It runs Kokoro, Piper, Kitten and " +
                    "Supertonic voices — some of the most natural-sounding free voices available — entirely on your phone.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "1. Tap Download on a voice below. It opens in your browser and saves to Downloads.\n" +
                    "2. Come back and tap Import, then pick the .tar.bz2 file.\n" +
                    "3. In Studio, choose the engine “OMARG Neural” and pick a voice.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "The app itself has no internet access — your browser does the download, so OMARG still can't send anything anywhere.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val progress = state.packProgress
            if (progress != null) {
                Text(progress)
                LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedButton(onClick = vm::cancelPackImport) { Text("Cancel") }
            } else {
                Button(onClick = { importer.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.FileOpen, null); Spacer(Modifier.width(8.dp)); Text("Import downloaded voice pack")
                }
            }
        }

        SectionCard("Installed (${state.packs.size})") {
            if (state.packs.isEmpty()) Text("No voice packs yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.packs.forEachIndexed { i, pack ->
                if (i > 0) HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(pack.name, fontWeight = FontWeight.SemiBold)
                        Text("${pack.kind} · ${pack.sizeBytes / (1024 * 1024)} MB", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { deleting = pack }) { Icon(Icons.Filled.Delete, "Delete ${pack.name}") }
                }
            }
        }

        SectionCard("Get voices") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(VoiceCatalog.groups) { g -> FilterChip(selected = g == group, onClick = { group = g }, label = { Text(g) }) }
            }
            VoiceCatalog.entries.filter { it.group == group }.forEachIndexed { i, entry ->
                if (i > 0) HorizontalDivider()
                CatalogRow(entry, installed = state.packs.any { it.id == entry.id }) { vm.openLink(entry.url) }
            }
            TextButton(onClick = { vm.openLink(CatalogEntry.ALL_MODELS) }) { Text("Browse all 200+ voices (any sherpa-onnx TTS .tar.bz2 works)") }
        }
        Spacer(Modifier.height(24.dp))
    }

    deleting?.let { pack ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${pack.name}?") },
            text = { Text("Frees ${pack.sizeBytes / (1024 * 1024)} MB. You can import it again later.") },
            confirmButton = { TextButton(onClick = { vm.deletePack(pack.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CatalogRow(entry: CatalogEntry, installed: Boolean, onDownload: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(entry.title, fontWeight = FontWeight.SemiBold)
            Text(entry.description, style = MaterialTheme.typography.bodySmall)
            Text(if (installed) "Installed" else entry.size, style = MaterialTheme.typography.labelSmall,
                color = if (installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDownload) { Icon(Icons.Filled.Download, "Download ${entry.title}") }
    }
}
