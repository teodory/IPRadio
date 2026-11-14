package com.example.ipradio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Bundle
import android.os.Process
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media.utils.MediaConstants
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var catalog: StationCatalog
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaLibrarySession
    private lateinit var songManager: SongManager
    private var handlingServiceStart = false
    private var rejectedServiceStart = false
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
        if (key == MUSICBRAINZ_ARTWORK_ENABLED) {
            songManager.setMusicBrainzEnabled(preferences.getBoolean(MUSICBRAINZ_ARTWORK_ENABLED, false))
        }
    }

    private val playbackListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val station = player.preparedStation()
            if (player.playWhenReady && station != null &&
                radioPreferences.getString(LAST_PLAYED_STATION_ID, null) != station.id
            ) {
                radioPreferences.edit().putString(LAST_PLAYED_STATION_ID, station.id).apply()
            }
            songManager.setStation(station, poll = player.playWhenReady)
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                player.currentMediaItem?.mediaId?.let { publishSong(it, null) }
            }
        }

        override fun onMetadata(metadata: Metadata) {
            val song = metadata.songInfo()
            val hasIcyTitle = (0 until metadata.length()).any { (metadata[it] as? IcyInfo)?.title != null }
            if (song != null || hasIcyTitle) updateStreamSong(song)
        }

        override fun onTracksChanged(tracks: Tracks) {
            for (group in tracks.groups) {
                if (group.type != C.TRACK_TYPE_AUDIO || !group.isSelected) continue
                for (index in 0 until group.length) {
                    if (!group.isTrackSelected(index)) continue
                    val song = group.getTrackFormat(index).metadata?.songInfo() ?: continue
                    updateStreamSong(song)
                    return
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        VoiceDiagnostics.record(this, "service_created")
        catalog = StationCatalog.getInstance(this)
        val dataSourceFactory = ResolvingDataSource.Factory(DefaultDataSource.Factory(this)) { request ->
            // Radio Antena rejects stream requests without its website as the Referer.
            if (request.uri.toString() == RADIO_ANTENA_STREAM_URL) {
                request.withAdditionalHeaders(mapOf("Referer" to "https://radioantena.bg/"))
            } else {
                request
            }
        }
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        songManager = SongManager(serviceScope, publish = ::publishSong)
        radioPreferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        songManager.setMusicBrainzEnabled(radioPreferences.getBoolean(MUSICBRAINZ_ARTWORK_ENABLED, false))
        player.addListener(playbackListener)

        val stationPlayer = StationPlayer(player, ::skipStation)
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        session = MediaLibrarySession.Builder(this, stationPlayer, LibraryCallback())
            .setId(UUID.randomUUID().toString())
            .setSessionActivity(openApp)
            .build()

        serviceScope.launch {
            catalog.stations.collect { stations ->
                applyCatalogUpdate(stations)
                session.notifyChildrenChanged(ROOT_ID, stations.size, null)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        // Media3 supplies an anonymous placeholder before asking the legacy browser to connect.
        // Only the binding path needs that placeholder; notification intents already target a session.
        if (controllerInfo.uid < 0) {
            val allowed = !handlingServiceStart
            VoiceDiagnostics.record(this, "session_request package=${controllerInfo.packageName} uid=${controllerInfo.uid} allowed=$allowed")
            if (allowed) return session
            rejectedServiceStart = true
            return null
        }
        val allowed = isTrustedController(controllerInfo)
        VoiceDiagnostics.record(this, "session_request package=${controllerInfo.packageName} uid=${controllerInfo.uid} allowed=$allowed")
        return session.takeIf { allowed }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handlingServiceStart = true
        rejectedServiceStart = false
        val result = try {
            super.onStartCommand(intent, flags, startId)
        } finally {
            handlingServiceStart = false
        }
        val playbackRequested = player.playWhenReady &&
            (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)
        val liveMediaButton = intent?.action == Intent.ACTION_MEDIA_BUTTON && !rejectedServiceStart
        if (playbackRequested || liveMediaButton) return result

        // An expired notification or an unknown caller can launch us as a foreground service.
        // Android requires promotion before we can stop an idle foreground service start.
        finishRejectedStart(startId)
        return START_NOT_STICKY
    }

    private fun finishRejectedStart(startId: Int) {
        val appName = getString(R.string.app_name)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(EXPIRED_ACTION_CHANNEL, appName, NotificationManager.IMPORTANCE_LOW)
        )
        val notification = NotificationCompat.Builder(this, EXPIRED_ACTION_CHANNEL)
            .setSmallIcon(R.drawable.radio_48px)
            .setContentTitle(appName)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .build()

        try {
            startForeground(EXPIRED_ACTION_NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: IllegalStateException) {
            // An ordinary background start might have no permission to promote itself.
        } finally {
            stopSelf(startId)
        }
    }

    override fun onDestroy() {
        VoiceDiagnostics.record(this, "service_destroyed")
        radioPreferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        serviceScope.cancel()
        player.removeListener(playbackListener)
        session.release()
        player.release()
        super.onDestroy()
    }

    private fun skipStation(offset: Int) {
        val station = adjacentStation(catalog.stations.value, player.currentMediaItem?.mediaId, offset)
            ?: return
        VoiceDiagnostics.record(this, "skip offset=$offset result=${station.id}")
        player.setMediaItem(station.mediaItem(playableSource = true))
        player.prepare()
        player.play()
    }

    private fun applyCatalogUpdate(stations: List<Station>) {
        val item = player.currentMediaItem ?: return
        val previous = item.localConfiguration?.tag as? Station ?: return
        val station = stations.firstOrNull { it.id == previous.id }
        if (station == previous) return
        if (station == null) {
            player.pause()
            player.stop()
            player.clearMediaItems()
            return
        }

        if (station.url != previous.url) {
            songManager.setStation(null)
            val shouldPrepare = player.playbackState == Player.STATE_BUFFERING ||
                player.playbackState == Player.STATE_READY
            if (!shouldPrepare) player.pause()
            player.setMediaItem(station.mediaItem(playableSource = true))
            if (shouldPrepare) player.prepare()
        } else {
            val sameMetadataSource = station.songDataUrl == previous.songDataUrl &&
                station.metadataFormat == previous.metadataFormat
            val song = songManager.currentSong.takeIf { sameMetadataSource }
            player.replaceMediaItem(0, station.mediaItem(playableSource = true, song = song))
            songManager.setStation(player.preparedStation(), poll = player.playWhenReady)
        }
    }

    private fun Player.preparedStation(): Station? {
        if (playbackState != Player.STATE_READY && playbackState != Player.STATE_BUFFERING) return null
        return currentMediaItem?.localConfiguration?.tag as? Station
    }

    private fun Metadata.songInfo(): SongInfo? {
        val metadata = MediaMetadata.Builder().populateFromMetadata(this).build()
        return parseStreamSong(metadata.title?.toString(), metadata.artist?.toString(), metadata.artworkData)
    }

    private fun updateStreamSong(song: SongInfo?) {
        val station = player.preparedStation() ?: return
        songManager.setStation(station, poll = player.playWhenReady)
        songManager.setStreamSong(station.id, song)
    }

    private fun publishSong(stationId: String, song: SongInfo?) {
        val item = player.currentMediaItem ?: return
        if (item.mediaId != stationId) return
        val station = item.localConfiguration?.tag as? Station ?: return
        val metadata = station.mediaMetadata(playableSource = true, song = song)
        if (item.mediaMetadata == metadata) return

        // The URI and playback configuration stay identical, so ExoPlayer updates the source in place.
        player.replaceMediaItem(0, item.buildUpon().setMediaMetadata(metadata).build())
    }

    private fun isTrustedController(controller: MediaSession.ControllerInfo): Boolean {
        if (controller.uid == Process.myUid() || controller.uid == Process.SYSTEM_UID) return true
        if (controller.uid < 0) return false
        val info = try {
            packageManager.getApplicationInfo(controller.packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return false
        }
        if (info.uid != controller.uid) return false
        val certificates = GOOGLE_MEDIA_CLIENTS[controller.packageName]
        return if (certificates != null) {
            certificates.any { certificate ->
                packageManager.hasSigningCertificate(
                    controller.packageName, certificate, PackageManager.CERT_INPUT_SHA256
                )
            }
        } else {
            info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        }
    }

    private fun Station.mediaItem(playableSource: Boolean = false, song: SongInfo? = null): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(mediaMetadata(playableSource, song))
            .apply { if (playableSource) setUri(url).setTag(this@mediaItem) }
            .build()

    private fun Station.mediaMetadata(playableSource: Boolean, song: SongInfo?): MediaMetadata {
        val resource = logo.takeIf { it != 0 } ?: R.drawable.radio_224dp_sz48
        val artwork = if (song?.artworkData != null) null else {
            (song?.artworkUrl ?: customArtworkUrl?.takeIf { playableSource })?.let(Uri::parse)
                ?: Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://$packageName/$resource")
        }
        val stationTitle = if (playableSource) "📻 $name" else name
        return MediaMetadata.Builder()
            .setTitle(song?.title ?: name)
            .setDisplayTitle(song?.artist?.let { "$stationTitle · $it" } ?: stationTitle)
            .setSubtitle(song?.title)
            .setArtist(song?.artist)
            .setArtworkUri(artwork)
            .setArtworkData(song?.artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            .build()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        private val root = MediaItem.Builder()
            .setMediaId(ROOT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(getString(R.string.app_name))
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    .build()
            )
            .build()

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val allowed = isTrustedController(controller)
            VoiceDiagnostics.record(this@PlaybackService, "controller_connect package=${controller.packageName} uid=${controller.uid} allowed=$allowed")
            if (!allowed) return MediaSession.ConnectionResult.reject()
            val commands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                .remove(Player.COMMAND_SET_REPEAT_MODE)
                .remove(Player.COMMAND_SET_SHUFFLE_MODE)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(commands)
                .build()
        }

        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            // Media3 resolves notification intents to a live session URI before reaching this
            // callback. Platform media-button dispatch supplies a caller UID and can bypass onConnect.
            val notificationAction = controllerInfo.uid < 0 &&
                controllerInfo.packageName == packageName && intent.data != null
            return !notificationAction && !isTrustedController(controllerInfo)
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val request = mediaItems.singleOrNull()
                ?: run {
                    VoiceDiagnostics.record(this@PlaybackService, "set_media_items package=${controller.packageName} count=${mediaItems.size} rejected")
                    return Futures.immediateFailedFuture(IllegalArgumentException("Select one station"))
                }
            val stations = catalog.stations.value
            val query = request.requestMetadata.searchQuery
            val station = when {
                request.requestMetadata.mediaUri != null -> null
                query == null -> stations.firstOrNull { it.id == request.mediaId }
                query.isBlank() -> stations.firstOrNull { it.id == player.currentMediaItem?.mediaId }
                    ?: stations.firstOrNull()
                else -> findStations(stations, query).firstOrNull()
            }
            VoiceDiagnostics.record(this@PlaybackService,
                "set_media_items package=${controller.packageName} search=${query.orEmpty()} media_id=${request.mediaId} result=${station?.id ?: "none"}")
            if (station == null) return Futures.immediateFailedFuture(IllegalArgumentException("Unknown station"))
            return Futures.immediateFuture(playbackSelection(station))
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val stations = catalog.stations.value
            val lastStationId = radioPreferences.getString(LAST_PLAYED_STATION_ID, null)
            val station = stations.firstOrNull { it.id == lastStationId } ?: stations.firstOrNull()
                ?: return Futures.immediateFailedFuture(IllegalStateException("No stations available"))
            VoiceDiagnostics.record(this@PlaybackService, "playback_resumption package=${controller.packageName} result=${station.id}")
            return Futures.immediateFuture(playbackSelection(station))
        }

        private fun playbackSelection(station: Station) = MediaSession.MediaItemsWithStartPosition(
            listOf(station.mediaItem(playableSource = true)), C.INDEX_UNSET, C.TIME_UNSET
        )

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            VoiceDiagnostics.record(this@PlaybackService, "browser_root package=${browser.packageName}")
            val extras = Bundle().apply {
                putBoolean(MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED, true)
                putInt(
                    MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                    MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
                )
            }
            return Futures.immediateFuture(
                LibraryResult.ofItem(root, LibraryParams.Builder().setExtras(extras).build())
            )
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = if (mediaId == ROOT_ID) root else {
                catalog.stations.value.firstOrNull { it.id == mediaId }?.mediaItem()
            }
            return Futures.immediateFuture(
                item?.let { LibraryResult.ofItem(it, null) }
                    ?: LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (parentId != ROOT_ID) {
                return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            }
            return stationPage(catalog.stations.value, page, pageSize, params)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            val count = findStations(catalog.stations.value, query).size
            VoiceDiagnostics.record(this@PlaybackService, "browser_search package=${browser.packageName} search=$query results=$count")
            session.notifySearchResultChanged(browser, query, count, params)
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val matches = findStations(catalog.stations.value, query)
            VoiceDiagnostics.record(this@PlaybackService,
                "browser_search_results package=${browser.packageName} search=$query results=${matches.size} page=$page")
            return stationPage(matches, page, pageSize, params)
        }

        private fun stationPage(
            stations: List<Station>,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val start = (page.toLong() * pageSize).coerceAtMost(stations.size.toLong()).toInt()
            val end = (start.toLong() + pageSize).coerceAtMost(stations.size.toLong()).toInt()
            return Futures.immediateFuture(
                LibraryResult.ofItemList(stations.subList(start, end).map { it.mediaItem() }, params)
            )
        }
    }

    private companion object {
        const val ROOT_ID = "com.example.ipradio.ROOT"
        const val LAST_PLAYED_STATION_ID = "last_played_station_id"
        const val EXPIRED_ACTION_CHANNEL = "expired_playback_action"
        const val EXPIRED_ACTION_NOTIFICATION = 1002

        // Production APK certificates and Android Auto's rotated release key from Android's UAMP sample.
        val GOOGLE_MEDIA_CLIENTS = mapOf(
            "com.google.android.projection.gearhead" to listOf(
                "fdb00c43dbde8b51cb312aa81d3b5fa17713adb94b28f598d77f8eb89daceedf",
                "1ca8dcc0bed3cbd872d2cb791200c0292ca9975768a82d676b8b424fb65b5295"
            ),
            "com.google.android.googlequicksearchbox" to listOf(
                "f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83"
            ),
            "com.google.android.carassistant" to listOf(
                "74b6fbf710e8d90d44d340125889b42306a62c4379d0e5a66220e3a68abf90e2"
            )
        ).mapValues { (_, fingerprints) ->
            fingerprints.map { hex -> hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray() }
        }
    }
}

internal fun findStations(stations: List<Station>, query: String): List<Station> {
    val name = query.trim()
    if (name.isEmpty()) return emptyList()
    return stations.filter { it.name.contains(name, ignoreCase = true) }
        .sortedByDescending { it.name.equals(name, ignoreCase = true) }
}
