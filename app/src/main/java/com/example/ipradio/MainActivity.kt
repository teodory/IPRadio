package com.example.ipradio

import android.app.SearchManager
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
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
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pendingVoiceSearch: String? = null
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
        handleVoiceIntent(intent)
        connectPlayer()

        setContent {
            val stations by catalog.stations.collectAsState()
            val allStations by catalog.allStations.collectAsState()
            val darkModeEnabled = darkModeOverride ?: isSystemInDarkTheme()
            IPRadioTheme(darkTheme = darkModeEnabled) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RadioScreen(
                        stations = stations,
                        allStations = allStations,
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
                        setStationEnabled = catalog::setEnabled,
                        moveStation = catalog::move
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleVoiceIntent(intent)
    }

    private fun handleVoiceIntent(intent: Intent?) {
        if (intent?.action != MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) return
        pendingVoiceSearch = intent.getStringExtra(SearchManager.QUERY).orEmpty()
        playPendingVoiceSearch()
    }

    private fun playPendingVoiceSearch() {
        val player = controller ?: return
        val query = pendingVoiceSearch ?: return
        pendingVoiceSearch = null
        player.setMediaItem(
            MediaItem.Builder().setRequestMetadata(
                MediaItem.RequestMetadata.Builder().setSearchQuery(query).build()
            ).build()
        )
        player.prepare()
        player.play()
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
                        playPendingVoiceSearch()
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
        val songTitle = metadata.subtitle?.toString().orEmpty()
        playback = PlaybackUiState(
            connected = true,
            stationId = player.currentMediaItem?.mediaId,
            active = active,
            playing = player.isPlaying,
            songTitle = songTitle,
            songArtist = metadata.artist?.toString()?.takeIf { songTitle.isNotBlank() && it.isNotBlank() },
            songArtwork = if (songTitle.isBlank()) null else metadata.artworkData
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
    val songTitle: String = "",
    val songArtist: String? = null,
    val songArtwork: Any? = null,
    @StringRes val status: Int? = null
)

@Composable
internal fun StationEditor(
    station: Station?,
    editor: StationEditorState,
    onDismiss: () -> Unit,
    saveStation: suspend (String?, String, String, String, String, Boolean, Int) -> Result<Station>
) {
    var name by rememberSaveable { mutableStateOf(station?.name.orEmpty()) }
    var url by rememberSaveable { mutableStateOf(station?.url.orEmpty()) }
    var image by rememberSaveable { mutableStateOf(station?.image.orEmpty()) }
    var infoUrl by rememberSaveable { mutableStateOf(station?.infoDataUrl.orEmpty()) }
    var gainDb by rememberSaveable { mutableStateOf(station?.gainDb ?: 0) }
    val gainDescription = stringResource(R.string.station_gain)
    val saving = editor.saving
    val error = editor.result?.exceptionOrNull()
    LaunchedEffect(editor.result) {
        if (editor.result?.isSuccess == true) onDismiss()
    }

    val panelShape = RoundedCornerShape(12.dp)
    val panelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val panelBorder = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (station == null) "Add New Radio" else "Edit Radio") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StationTextField(name, { name = it }, "Name", !saving)
                StationTextField(url, { url = it }, "URL", !saving)
                StationTextField(image, { image = it }, "Image URL", !saving)
                StationTextField(infoUrl, { infoUrl = it }, "Info URL", !saving)
                Column(
                    Modifier.fillMaxWidth().clip(panelShape).background(panelColor)
                        .border(1.dp, panelBorder, panelShape)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.station_gain), Modifier.weight(1f))
                        Text(stringResource(R.string.station_gain_value,
                            if (gainDb > 0) "+$gainDb" else gainDb.toString()))
                    }
                    Slider(
                        value = gainDb.toFloat(),
                        onValueChange = { gainDb = it.roundToInt() },
                        modifier = Modifier.fillMaxWidth().semantics {
                            contentDescription = gainDescription
                        },
                        enabled = !saving,
                        valueRange = -12f..12f,
                        steps = 23
                    )
                }
                if (error != null) {
                    Text(error.localizedMessage ?: "Couldn't save station.", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving,
                onClick = { editor.save(station?.id, name, url, image, infoUrl, station?.enabled ?: true, gainDb, saveStation) }
            ) {
                Text(if (station == null) "Add" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}

@Composable
private fun StationTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean
) {
    val shape = RoundedCornerShape(12.dp)
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().clip(shape)
            .background(colors.onSurface.copy(alpha = 0.08f))
            .border(1.dp, if (focused) colors.primary else colors.onSurface.copy(alpha = 0.14f), shape),
        enabled = enabled,
        label = { Text(label) },
        singleLine = true,
        shape = shape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        interactionSource = interactionSource
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
        enabled: Boolean,
        gainDb: Int,
        saveStation: suspend (String?, String, String, String, String, Boolean, Int) -> Result<Station>
    ) {
        if (saving) return
        saving = true
        result = null
        viewModelScope.launch {
            result = saveStation(id, name, url, image, infoUrl, enabled, gainDb)
            saving = false
        }
    }

    fun clearResult() {
        result = null
    }
}
