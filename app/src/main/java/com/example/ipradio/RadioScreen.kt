package com.example.ipradio

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun RadioScreen(
    stations: List<Station>,
    allStations: List<Station>,
    playback: PlaybackUiState,
    editor: StationEditorState,
    musicBrainzEnabled: Boolean,
    onMusicBrainzChanged: (Boolean) -> Unit,
    bigPlayerEnabled: Boolean,
    onBigPlayerChanged: (Boolean) -> Unit,
    darkModeEnabled: Boolean,
    onDarkModeChanged: (Boolean) -> Unit,
    onPlayStation: (Station) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    saveStation: suspend (String?, String, String, String, String, Boolean, Int) -> Result<Station>,
    setStationEnabled: suspend (String, Boolean) -> Result<Boolean>,
    moveStation: suspend (String, Int) -> Result<Boolean>
) {
    var editMode by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var moving by remember { mutableStateOf(false) }
    var moveFailed by rememberSaveable { mutableStateOf(false) }
    var changingAvailability by remember { mutableStateOf(false) }
    var availabilityFailed by rememberSaveable { mutableStateOf(false) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var draggedOffset by remember { mutableStateOf(0f) }
    var dragStartIndex by remember { mutableStateOf(-1) }
    var dragPointerY by remember { mutableStateOf(0f) }
    var previewOrder by remember { mutableStateOf<List<Station>?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val dragScrollEdge = with(LocalDensity.current) { 64.dp.toPx() }
    val selectedIndex = stations.indexOfFirst { it.id == playback.stationId }
    val visibleStations = if (editMode) previewOrder ?: allStations else stations
    BackHandler(enabled = settingsOpen || editMode) {
        if (settingsOpen) settingsOpen = false else editMode = false
    }
    LaunchedEffect(selectedIndex, editMode, bigPlayerEnabled) {
        if (editMode || selectedIndex < 0) return@LaunchedEffect
        val layout = listState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
        if (item == null || item.offset < layout.viewportStartOffset ||
            item.offset + item.size > layout.viewportEndOffset
        ) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    fun move(id: String, offset: Int) {
        if (moving || changingAvailability) return
        moving = true
        scope.launch {
            try {
                val result = moveStation(id, offset)
                if (result.isFailure) moveFailed = true
            } finally {
                previewOrder = null
                moving = false
            }
        }
    }

    fun changeAvailability(id: String, enabled: Boolean) {
        if (moving || changingAvailability) return
        changingAvailability = true
        scope.launch {
            try {
                if (setStationEnabled(id, enabled).isFailure) availabilityFailed = true
            } finally {
                changingAvailability = false
            }
        }
    }

    fun reorderUnderPointer() {
        val id = draggedId ?: return
        val order = previewOrder ?: return
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        val center = item.offset + item.size / 2f + draggedOffset
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key != id && center >= it.offset && center < it.offset + it.size
        } ?: return
        val from = order.indexOfFirst { it.id == id }
        val to = target.index
        if (from < 0 || to !in order.indices || order[to].enabled != order[from].enabled) return
        previewOrder = order.toMutableList().apply { add(to, removeAt(from)) }
        draggedOffset += item.offset - target.offset
    }

    LaunchedEffect(draggedId) {
        while (draggedId != null) {
            val layout = listState.layoutInfo
            val scroll = when {
                dragPointerY < layout.viewportStartOffset + dragScrollEdge -> -12f
                dragPointerY > layout.viewportEndOffset - dragScrollEdge -> 12f
                else -> 0f
            }
            if (scroll != 0f) {
                val consumed = listState.scrollBy(scroll)
                draggedOffset += consumed
                reorderUnderPointer()
            }
            delay(16)
        }
    }

    if (settingsOpen) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = { settingsOpen = false }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back_to_stations))
                }
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) {
                        editMode = true
                        settingsOpen = false
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.edit_stations), modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                SettingsSwitch(R.string.dark_mode, darkModeEnabled, onDarkModeChanged)
                SettingsSwitch(R.string.big_player, bigPlayerEnabled, onBigPlayerChanged)
                SettingsSwitch(
                    R.string.musicbrainz_artwork,
                    musicBrainzEnabled,
                    onMusicBrainzChanged,
                    description = R.string.musicbrainz_artwork_description
                )
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 10.dp, end = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.stations),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            if (editMode) {
                IconButton(onClick = { adding = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_radio))
                }
                IconButton(
                    onClick = { editMode = false },
                    modifier = Modifier.clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.done_editing_stations))
                }
                Spacer(Modifier.width(8.dp))
            }
            IconButton(
                onClick = { settingsOpen = true },
                modifier = Modifier.clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
            }
        }
        key(editMode) {
            LazyColumn(
                Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(visibleStations, key = { _, station -> station.id }) { index, station ->
                    val selected = playback.stationId == station.id
                    StationRow(
                        station = station,
                        isSelected = selected,
                        onAir = selected && playback.playing,
                        enabled = !editMode && playback.connected && station.enabled,
                        onPlay = { onPlayStation(station) },
                        onEdit = if (editMode) ({ editingId = station.id }) else null,
                        onEnabledChange = if (editMode && !moving && !changingAvailability && draggedId == null) {
                            { enabled -> changeAvailability(station.id, enabled) }
                        } else null,
                        onMoveUp = if (editMode && !moving && !changingAvailability &&
                            index > 0 && visibleStations[index - 1].enabled == station.enabled
                        ) ({ move(station.id, -1) }) else null,
                        onMoveDown = if (editMode && !moving && !changingAvailability &&
                            index < visibleStations.lastIndex && visibleStations[index + 1].enabled == station.enabled
                        ) ({ move(station.id, 1) }) else null,
                        dragging = draggedId == station.id,
                        dragOffset = if (draggedId == station.id) draggedOffset else 0f,
                        onDragStart = if (editMode && !moving && !changingAvailability) ({
                            draggedId = station.id
                            dragStartIndex = visibleStations.indexOfFirst { it.id == station.id }
                            previewOrder = visibleStations
                            draggedOffset = 0f
                            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == station.id }
                            dragPointerY = item?.let { it.offset + it.size / 2f } ?: 0f
                        }) else null,
                        onDrag = { delta ->
                            if (draggedId == station.id) {
                                draggedOffset += delta
                                dragPointerY += delta
                                reorderUnderPointer()
                            }
                        },
                        onDragEnd = {
                            if (draggedId == station.id) {
                                val destination = previewOrder?.indexOfFirst { it.id == station.id } ?: -1
                                draggedId = null
                                draggedOffset = 0f
                                if (destination >= 0 && destination != dragStartIndex) {
                                    move(station.id, destination - dragStartIndex)
                                } else {
                                    previewOrder = null
                                }
                            }
                        },
                        onDragCancel = {
                            if (draggedId == station.id) {
                                draggedId = null
                                draggedOffset = 0f
                                previewOrder = null
                            }
                        }
                    )
                }
            }
        }
        val station = stations.getOrNull(selectedIndex) ?: stations.firstOrNull()
        PlayerCard(
            station = station,
            playback = playback,
            big = bigPlayerEnabled,
            onPrevious = onPrevious,
            onNext = onNext,
            onToggle = { if (playback.active) onStop() else station?.let(onPlayStation) }
        )
    }

    if (moveFailed) {
        AlertDialog(
            onDismissRequest = { moveFailed = false },
            text = { Text(stringResource(R.string.station_order_error)) },
            confirmButton = {
                TextButton(onClick = { moveFailed = false }) { Text(stringResource(android.R.string.ok)) }
            }
        )
    }

    if (availabilityFailed) {
        AlertDialog(
            onDismissRequest = { availabilityFailed = false },
            text = { Text(stringResource(R.string.station_availability_error)) },
            confirmButton = {
                TextButton(onClick = { availabilityFailed = false }) { Text(stringResource(android.R.string.ok)) }
            }
        )
    }

    val editingStation = allStations.firstOrNull { it.id == editingId }
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
private fun SettingsSwitch(
    @StringRes title: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    @StringRes description: Int? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title))
            if (description != null) {
                Text(stringResource(description), style = MaterialTheme.typography.bodySmall)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun StationRow(
    station: Station,
    isSelected: Boolean,
    onAir: Boolean,
    enabled: Boolean,
    onPlay: () -> Unit,
    onEdit: (() -> Unit)?,
    onEnabledChange: ((Boolean) -> Unit)?,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    dragging: Boolean,
    dragOffset: Float,
    onDragStart: (() -> Unit)?,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val dragStart by rememberUpdatedState(onDragStart)
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val dragCancel by rememberUpdatedState(onDragCancel)
    val dragLabel = stringResource(R.string.drag_station, station.name)
    val availabilityLabel = stringResource(
        if (station.enabled) R.string.disable_station else R.string.enable_station, station.name
    )
    val upLabel = stringResource(R.string.move_station_up, station.name)
    val downLabel = stringResource(R.string.move_station_down, station.name)
    Row(
        modifier = Modifier.fillMaxWidth()
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffset }
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) colors.primaryContainer else colors.background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onPlay)
            .semantics { selected = isSelected }
            .heightIn(min = 56.dp)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StationArtwork(station, 38.dp)
        Text(
            station.displayName(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
        )
        if (!station.enabled && onEdit != null) {
            Text(stringResource(R.string.station_disabled), style = MaterialTheme.typography.labelMedium)
        }
        if (onAir && onEdit == null) {
            Text(
                stringResource(R.string.station_on_air),
                color = colors.primary,
                style = MaterialTheme.typography.labelMedium
            )
        }
        if (onEdit != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_radio, station.name))
                }
                Switch(
                    checked = station.enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = onEnabledChange != null,
                    modifier = Modifier.semantics { contentDescription = availabilityLabel }
                )
                Box(
                    modifier = Modifier.size(48.dp)
                        .pointerInput(station.id) {
                            detectDragGestures(
                                onDragStart = { dragStart?.invoke() },
                                onDragEnd = { dragEnd() },
                                onDragCancel = { dragCancel() },
                                onDrag = { change, amount ->
                                    change.consume()
                                    if (dragStart != null) drag(amount.y)
                                }
                            )
                        }
                        .semantics {
                            contentDescription = dragLabel
                            customActions = listOfNotNull(
                                onMoveUp?.let { CustomAccessibilityAction(upLabel) { it(); true } },
                                onMoveDown?.let { CustomAccessibilityAction(downLabel) { it(); true } }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Menu, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun PlayerCard(
    station: Station?,
    playback: PlaybackUiState,
    big: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggle: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val stackedBigPlayer = big && LocalConfiguration.current.screenHeightDp >= 420
    val artworkSize = when {
        stackedBigPlayer -> 80.dp
        big -> 56.dp
        else -> 38.dp
    }
    val enabled = playback.connected && station != null
    val canSkip = enabled && playback.stationId != null
    val songTitle = playback.songTitle.takeIf { playback.playing && it.isNotBlank() }
    val subtitle = when {
        playback.connectionError -> stringResource(R.string.player_unavailable)
        !playback.connected -> stringResource(R.string.player_connecting)
        playback.status != null -> stringResource(playback.status)
        station != null -> stringResource(R.string.player_ready)
        else -> ""
    }
    val stationDetails: @Composable (Modifier) -> Unit = { modifier ->
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (big) 14.dp else 7.dp)
        ) {
            StationArtwork(station, artworkSize, playback.songArtwork.takeIf { playback.playing })
            Column(
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    station?.displayName()
                        ?: stringResource(R.string.player_choose_station),
                    style = when {
                        stackedBigPlayer -> MaterialTheme.typography.headlineSmall
                        big -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (songTitle != null || subtitle.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (playback.playing) {
                            Box(Modifier.padding(top = 6.dp).size(6.dp).background(colors.primary, CircleShape))
                        }
                        val textStyle = if (big) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall
                        if (songTitle != null) {
                            Column(Modifier.weight(1f)) {
                                playback.songArtist?.let { artist ->
                                    Text(artist, style = textStyle, color = colors.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(songTitle, style = textStyle, color = colors.onSurfaceVariant,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        } else {
                            Text(
                                subtitle,
                                style = textStyle,
                                color = if (playback.status == R.string.player_stream_error) colors.error else colors.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
    val controls: @Composable (Modifier) -> Unit = { modifier ->
        Row(
            modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (stackedBigPlayer) 12.dp else 0.dp)
        ) {
            IconButton(onClick = onPrevious, enabled = canSkip) {
                Icon(painterResource(R.drawable.ic_skip_previous), stringResource(R.string.player_previous))
            }
            FilledIconButton(onClick = onToggle, enabled = enabled, modifier = Modifier.size(if (big) 56.dp else 48.dp)) {
                if (playback.active) {
                    Icon(painterResource(R.drawable.ic_stop), stringResource(R.string.player_stop))
                } else {
                    Icon(Icons.Default.PlayArrow, stringResource(R.string.player_play))
                }
            }
            IconButton(onClick = onNext, enabled = canSkip) {
                Icon(painterResource(R.drawable.ic_skip_next), stringResource(R.string.player_next))
            }
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant),
        shadowElevation = 2.dp
    ) {
        val padding = if (big) PaddingValues(14.dp) else PaddingValues(start = 9.dp, end = 4.dp, top = 7.dp, bottom = 7.dp)
        BoxWithConstraints(Modifier.padding(padding)) {
            if (stackedBigPlayer) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    stationDetails(Modifier.fillMaxWidth())
                    controls(Modifier)
                }
            } else if (maxWidth < 320.dp || LocalDensity.current.fontScale > 1.25f) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    stationDetails(Modifier.fillMaxWidth().padding(end = 5.dp))
                    controls(Modifier.align(Alignment.End))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    stationDetails(Modifier.weight(1f))
                    controls(Modifier)
                }
            }
        }
    }
}

@Composable
private fun StationArtwork(station: Station?, size: Dp, songArtwork: Any? = null) {
    val context = LocalContext.current
    val logo = station?.logo?.takeIf { it != 0 }
    val image = station?.customArtworkUrl
    val fallback = painterResource(logo ?: R.drawable.radio_224dp_sz48)
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (image == null) {
            Image(fallback, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            AsyncImage(
                model = image,
                contentDescription = null,
                placeholder = fallback,
                error = fallback,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (songArtwork != null && songArtwork.toString() != image) {
            AsyncImage(
                model = songArtwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = {
                    MetadataDiagnostics.record(context,
                        "artwork_loaded station=${station?.id} model=${songArtwork.toString().take(180)}")
                },
                onError = {
                    MetadataDiagnostics.record(context,
                        "artwork_failed station=${station?.id} model=${songArtwork.toString().take(180)} error=${it.result.throwable.javaClass.simpleName}: ${it.result.throwable.message}")
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
