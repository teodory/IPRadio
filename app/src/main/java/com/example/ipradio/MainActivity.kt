package com.example.ipradio

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.ipradio.ui.theme.IPRadioTheme
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var playback by mutableStateOf(PlaybackUiState())
    private val playbackListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            showPlayback(player)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val catalog = StationCatalog.getInstance(this)
        val editor = ViewModelProvider(this)[StationEditorState::class.java]
        connectPlayer()

        setContent {
            val stations by catalog.stations.collectAsState()
            IPRadioTheme {
                Surface(Modifier.fillMaxSize()) {
                    RadioScreen(
                        stations = stations,
                        playback = playback,
                        editor = editor,
                        onStationClick = ::playStation,
                        onPrevious = { controller?.seekToPrevious() },
                        onNext = { controller?.seekToNext() },
                        onStop = ::stopPlayback,
                        saveStation = catalog::save
                    )
                }
            }
        }
    }

    private fun connectPlayer() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    if (this@MainActivity.controller === controller) {
                        this@MainActivity.controller = null
                        playback = PlaybackUiState(connectionError = true)
                    }
                }
            })
            .buildAsync()
        controllerFuture = future
        future.addListener({
            if (controllerFuture === future) {
                runCatching { future.get() }
                    .onSuccess { player ->
                        controller = player
                        player.addListener(playbackListener)
                        showPlayback(player)
                    }
                    .onFailure { playback = PlaybackUiState(connectionError = true) }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun playStation(station: Station) {
        val player = controller ?: return
        if (playback.stationId == station.id && playback.active) {
            stopPlayback()
        } else {
            player.setMediaItem(MediaItem.Builder().setMediaId(station.id).build())
            player.prepare()
            player.play()
        }
    }

    private fun stopPlayback() {
        controller?.apply {
            pause()
            stop()
        }
    }

    private fun showPlayback(player: Player) {
        val active = player.playWhenReady &&
            (player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY)
        playback = PlaybackUiState(
            connected = true,
            stationId = player.currentMediaItem?.mediaId,
            active = active,
            song = player.mediaMetadata.subtitle?.toString().orEmpty(),
            status = when {
                player.playerError != null -> "Couldn't play this stream."
                active && player.playbackState == Player.STATE_BUFFERING -> "Connecting to station…"
                player.isPlaying -> "Playing"
                active -> "Waiting for audio…"
                player.playbackState == Player.STATE_READY -> "Paused"
                else -> null
            }
        )
    }

    override fun onDestroy() {
        controller?.removeListener(playbackListener)
        val future = controllerFuture
        controllerFuture = null
        controller = null
        future?.let(MediaController::releaseFuture)
        super.onDestroy()
    }
}

private data class PlaybackUiState(
    val connected: Boolean = false,
    val connectionError: Boolean = false,
    val stationId: String? = null,
    val active: Boolean = false,
    val song: String = "",
    val status: String? = null
)

@Composable
private fun RadioScreen(
    stations: List<Station>,
    playback: PlaybackUiState,
    editor: StationEditorState,
    onStationClick: (Station) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    saveStation: suspend (String?, String, String, String, String) -> Result<Station>
) {
    var editMode by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Stations", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            if (editMode) {
                IconButton(onClick = { adding = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Radio")
                }
            }
            IconButton(onClick = { editMode = !editMode }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
        Divider()
        LazyColumn(Modifier.weight(1f)) {
            items(stations, key = { it.id }) { station ->
                val active = playback.active && playback.stationId == station.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = playback.connected) { onStationClick(station) }
                        .padding(start = 12.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        station.name,
                        modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    if (editMode) {
                        IconButton(onClick = { editingId = station.id }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit ${station.name}")
                        }
                    }
                }
            }
        }
        Divider()
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            when {
                playback.connectionError -> Text("Player unavailable.")
                !playback.connected -> Text("Connecting to player…")
                else -> {
                    playback.status?.let { Text(it) }
                    if (playback.song.isNotBlank()) Text(playback.song)
                }
            }
            Row {
                TextButton(onClick = onPrevious, enabled = playback.connected) { Text("Prev") }
                TextButton(onClick = onStop, enabled = playback.connected) { Text("Stop") }
                TextButton(onClick = onNext, enabled = playback.connected) { Text("Next") }
            }
        }
    }

    val editingStation = stations.firstOrNull { it.id == editingId }
    if (adding || editingStation != null) {
        key(editingId) {
            StationEditor(
                station = editingStation,
                editor = editor,
                onDismiss = {
                    editor.clearResult()
                    adding = false
                    editingId = null
                },
                saveStation = saveStation
            )
        }
    }
}

@Composable
private fun StationEditor(
    station: Station?,
    editor: StationEditorState,
    onDismiss: () -> Unit,
    saveStation: suspend (String?, String, String, String, String) -> Result<Station>
) {
    var name by rememberSaveable { mutableStateOf(station?.name.orEmpty()) }
    var url by rememberSaveable { mutableStateOf(station?.url.orEmpty()) }
    var image by rememberSaveable { mutableStateOf(station?.image.orEmpty()) }
    var infoUrl by rememberSaveable { mutableStateOf(station?.infoDataUrl.orEmpty()) }
    val saving = editor.saving
    val error = editor.result?.exceptionOrNull()
    LaunchedEffect(editor.result) {
        if (editor.result?.isSuccess == true) onDismiss()
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (station == null) "Add New Radio" else "Edit Radio") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextField(name, { name = it }, enabled = !saving, label = { Text("Name") }, singleLine = true)
                TextField(url, { url = it }, enabled = !saving, label = { Text("URL") }, singleLine = true)
                TextField(image, { image = it }, enabled = !saving, label = { Text("Image URL") }, singleLine = true)
                TextField(infoUrl, { infoUrl = it }, enabled = !saving, label = { Text("Info URL") }, singleLine = true)
                if (error != null) {
                    Text(error.localizedMessage ?: "Couldn't save station.", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving,
                onClick = { editor.save(station?.id, name, url, image, infoUrl, saveStation) }
            ) {
                Text(if (station == null) "Add" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}

internal class StationEditorState : ViewModel() {
    var saving by mutableStateOf(false)
        private set
    var result by mutableStateOf<Result<Station>?>(null)
        private set

    fun save(
        id: String?,
        name: String,
        url: String,
        image: String,
        infoUrl: String,
        saveStation: suspend (String?, String, String, String, String) -> Result<Station>
    ) {
        if (saving) return
        saving = true
        result = null
        viewModelScope.launch {
            result = saveStation(id, name, url, image, infoUrl)
            saving = false
        }
    }

    fun clearResult() {
        result = null
    }
}
