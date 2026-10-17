package com.aetherengineers.omarg

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aetherengineers.omarg.ui.LibraryScreen
import com.aetherengineers.omarg.ui.MainViewModel
import com.aetherengineers.omarg.ui.SettingsScreen
import com.aetherengineers.omarg.ui.StudioScreen
import com.aetherengineers.omarg.ui.UiEvent
import com.aetherengineers.omarg.ui.theme.OmargTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIncoming(intent)
        setContent {
            OmargTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                val playback by vm.player.state.collectAsStateWithLifecycle()
                var tab by rememberSaveable { mutableIntStateOf(0) }
                val snackbar = remember { SnackbarHostState() }
                LaunchedEffect(Unit) {
                    vm.eventFlow.collect { e ->
                        when (e) {
                            is UiEvent.Message -> snackbar.showSnackbar(e.text)
                            is UiEvent.Launch -> runCatching { startActivity(e.intent) }
                        }
                    }
                }
                Scaffold(
                    topBar = { TopAppBar(title = { Text(listOf("OMARG Narrator", "Library", "Settings")[tab]) }) },
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.RecordVoiceOver, null) }, label = { Text("Studio") })
                            NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.LibraryMusic, null) }, label = { Text("Library") })
                            NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.Settings, null) }, label = { Text("Settings") })
                        }
                    },
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        when (tab) {
                            0 -> StudioScreen(vm, state, playback)
                            1 -> LibraryScreen(vm, state, playback, openStudio = { tab = 0 })
                            else -> SettingsScreen(vm, state)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    /** Accepts text shared from other apps or selected via the "Narrate with OMARG" text-selection action. */
    private fun handleIncoming(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        if (!text.isNullOrBlank()) vm.setText(text)
    }
}
