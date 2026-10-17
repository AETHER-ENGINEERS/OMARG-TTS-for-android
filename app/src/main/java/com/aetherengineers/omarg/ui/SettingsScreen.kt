package com.aetherengineers.omarg.ui

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import com.aetherengineers.omarg.data.ExportFormat

@Composable
fun SettingsScreen(vm: MainViewModel, state: UiState) {
    val context = LocalContext.current
    fun launch(intent: Intent) = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard("Export") {
            ExportFormat.entries.forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = state.exportFormat == f, onClick = { vm.setExportFormat(f) })
                    Text(f.label)
                }
            }
        }
        SectionCard("Sound") {
            SwitchRow("Trim silence between sentences", state.trimSilence, vm::setTrimSilence)
            SwitchRow("Normalize loudness", state.normalize, vm::setNormalize)
            LabeledSlider("Gap between sentences", state.sentenceGapMs.toFloat(), 0f..1500f, "${state.sentenceGapMs} ms") {
                vm.setSentenceGap((it / 50).toInt() * 50)
            }
        }
        SectionCard("Voices & engines") {
            Text(
                "OMARG has its own built-in neural engine (add voices in the Voices tab) and also works with every " +
                    "text-to-speech engine installed on your phone — on a Galaxy S22 that is Samsung TTS and Speech " +
                    "Services by Google, plus any you add such as RHVoice or eSpeak NG. Pick one under Voice → Engine.",
                style = MaterialTheme.typography.bodyMedium,
            )
            SwitchRow("Show online-only voices", state.allowNetworkVoices, vm::setAllowNetworkVoices)
            Text(
                "Off by default: online voices send your text to the engine maker's servers. Offline voices never leave the phone.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { launch(Intent("com.android.settings.TTS_SETTINGS")) }) { Text("System text-to-speech settings") }
            OutlinedButton(onClick = { launch(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }) { Text("Download more voices") }
        }
        SectionCard("Script tips") {
            Text(
                "• [pause 1s], [pause 500ms] — add silence\n" +
                    "• [1] [2] [3] [4] — switch voices in Dialogue mode\n" +
                    "• No length limit: long texts are split at sentence boundaries automatically\n" +
                    "• Select text in any app → ⋮ → \"Narrate with OMARG\", or share text to OMARG",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        SectionCard("Privacy") {
            Text(
                "OMARG has no internet permission, no ads, no analytics, no accounts and no subscription. " +
                    "Your scripts, voices and recordings are stored only in the app's private storage on this device " +
                    "and are deleted when you uninstall it.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
