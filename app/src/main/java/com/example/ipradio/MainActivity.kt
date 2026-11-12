package com.example.ipradio

import android.content.ComponentName
import android.content.ContentResolver
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    private var musicBrainzEnabled by mutableStateOf(false)
    private var bigPlayerEnabled by mutableStateOf(false)
    private var darkModeOverride by mutableStateOf<Boolean?>(null)
    private val playbackListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            showPlayback(player)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val catalog = StationCatalog.getInstance(this)
        val editor = ViewModelProvider(this)[StationEditorState::class.java]
        musicBrainzEnabled = radioPreferences.getBoolean(MUSICBRAINZ_ARTWORK_ENABLED, false)
        bigPlayerEnabled = radioPreferences.getBoolean(BIG_PLAYER_ENABLED, false)
        darkModeOverride = if (radioPreferences.contains(DARK_MODE_ENABLED)) {
            radioPreferences.getBoolean(DARK_MODE_ENABLED, false)
        } else {
            null
        }
        connectPlayer()

        setContent {
            val stations by catalog.stations.collectAsState()
            val darkModeEnabled = darkModeOverride ?: isSystemInDarkTheme()
            IPRadioTheme(darkTheme = darkModeEnabled) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RadioScreen(
                        stations = stations,
                        playback = playback,
                        editor = editor,
                        musicBrainzEnabled = musicBrainzEnabled,
                        onMusicBrainzChanged = { enabled ->
                            musicBrainzEnabled = enabled
                            radioPreferences.edit().putBoolean(MUSICBRAINZ_ARTWORK_ENABLED, enabled).apply()
                        },
                        bigPlayerEnabled = bigPlayerEnabled,
                        onBigPlayerChanged = { enabled ->
                            bigPlayerEnabled = enabled
                            radioPreferences.edit().putBoolean(BIG_PLAYER_ENABLED, enabled).apply()
                        },
                        darkModeEnabled = darkModeEnabled,
                        onDarkModeChanged = { enabled ->
                            darkModeOverride = enabled
                            radioPreferences.edit().putBoolean(DARK_MODE_ENABLED, enabled).apply()
                        },
                        onPlayStation = ::playStation,
                        onPrevious = { controller?.seekToPrevious() },
                        onNext = { controller?.seekToNext() },
                        onStop = ::stopPlayback,
                        saveStation = catalog::save,
                        moveStation = catalog::move
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
        if (playback.stationId == station.id && playback.active) return
        player.setMediaItem(MediaItem.Builder().setMediaId(station.id).build())
        player.prepare()
        player.play()
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
        val metadata = player.mediaMetadata
        val song = metadata.subtitle?.toString().orEmpty()
        playback = PlaybackUiState(
            connected = true,
            stationId = player.currentMediaItem?.mediaId,
            active = active,
            playing = player.isPlaying,
            song = song,
            songArtwork = if (song.isBlank()) null else metadata.artworkData
                ?: metadata.artworkUri?.takeUnless { it.scheme == ContentResolver.SCHEME_ANDROID_RESOURCE },
            status = when {
                player.playerError != null -> R.string.player_stream_error
                active && player.playbackState == Player.STATE_BUFFERING -> R.string.player_connecting_station
                player.isPlaying -> R.string.player_live
                active -> R.string.player_waiting
                player.playbackState == Player.STATE_READY -> R.string.player_paused
                player.currentMediaItem != null -> R.string.player_stopped
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

internal data class PlaybackUiState(
    val connected: Boolean = false,
    val connectionError: Boolean = false,
    val stationId: String? = null,
    val active: Boolean = false,
    val playing: Boolean = false,
    val song: String = "",
    val songArtwork: Any? = null,
    @StringRes val status: Int? = null
)

@Composable
internal fun StationEditor(
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
