package com.example.ipradio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal class SongManager(
    private val scope: CoroutineScope,
    private val fetch: suspend (String) -> String? = { fetchMetadata(it) },
    private val waitForNextPoll: suspend (Long) -> Unit = { delay(it) },
    private val enrichSong: suspend (SongInfo) -> SongInfo = MusicBrainzLookup()::enrich,
    private val publish: (stationId: String, song: SongInfo?) -> Unit,
) {
    private data class Source(
        val stationId: String,
        val streamUrl: String,
        val infoUrl: String,
        val format: MetadataFormat?
    )

    private var source: Source? = null
    private var stationName: String? = null
    private var pollJob: Job? = null
    private var lookupJob: Job? = null
    private var stationSong: SongInfo? = null
    private var stationSongAvailable = false
    private var streamSong: SongInfo? = null
    private var streamSongChangedSinceStationSong = false
    private var songWithoutLookup: SongInfo? = null
    private var musicBrainzEnabled = false
    var currentSong: SongInfo? = null
        private set

    // Called on the playback scope's dispatcher, which also receives metadata updates.
    fun setStation(station: Station?, poll: Boolean = true) {
        stationName = station?.name
        val nextSource = station?.let {
            Source(it.id, it.url, it.songDataUrl, it.metadataFormat)
        }
        if (source != nextSource) {
            val sameStream = nextSource != null && source?.stationId == nextSource.stationId &&
                source?.streamUrl == nextSource.streamUrl
            source = nextSource
            pollJob?.cancel()
            pollJob = null
            lookupJob?.cancel()
            lookupJob = null
            stationSong = null
            stationSongAvailable = false
            if (!sameStream) streamSong = null
            streamSongChangedSinceStationSong = false
            songWithoutLookup = null
            currentSong = null
            if (sameStream) updateSong()
        }
        if (!poll) {
            pollJob?.cancel()
            pollJob = null
            return
        }

        if (pollJob != null || nextSource == null || nextSource.format == null || nextSource.infoUrl.isBlank()) return
        pollJob = scope.launch {
            var delayIndex = 0
            while (isActive) {
                val song = try {
                    withContext(Dispatchers.IO) {
                        fetch(nextSource.infoUrl)?.let { parseSong(nextSource.format, it, nextSource.infoUrl) }
                    }
                } catch (_: IOException) {
                    null
                }
                ensureActive()
                val available = song != null && !isStationLabel(song)
                if (available) {
                    if (song != stationSong) {
                        delayIndex = 0
                        streamSongChangedSinceStationSong = false
                    }
                    stationSong = song
                }
                stationSongAvailable = available
                updateSong()
                waitForNextPoll(pollDelaysMs[delayIndex])
                delayIndex = (delayIndex + 1).coerceAtMost(pollDelaysMs.lastIndex)
            }
        }
    }

    fun setStreamSong(stationId: String, song: SongInfo?) {
        if (source?.stationId != stationId) return
        val next = song?.takeUnless(::isStationLabel)
        if (streamSong == next) return
        streamSong = next
        streamSongChangedSinceStationSong = true
        updateSong()
    }

    fun setMusicBrainzEnabled(enabled: Boolean) {
        if (musicBrainzEnabled == enabled) return
        musicBrainzEnabled = enabled
        updateSong(preferenceChanged = true)
    }

    private fun isStationLabel(song: SongInfo): Boolean {
        if (song.artist != null) return false
        val title = song.title.normalizedSongText().replace(" ", "")
        val builtInName = defaultStationsById[source?.stationId]?.name
        return listOfNotNull(stationName, builtInName).any {
            it.normalizedSongText().replace(" ", "") == title
        }
    }

    private fun updateSong(preferenceChanged: Boolean = false) {
        val stationId = source?.stationId ?: return
        val song = preferredSong()
        if (!preferenceChanged && songWithoutLookup == song) return
        songWithoutLookup = song
        lookupJob?.cancel()
        lookupJob = null
        publishSong(stationId, song)
        if (!musicBrainzEnabled || song?.artist == null) return

        lookupJob = scope.launch {
            val enriched = try {
                enrichSong(song)
            } catch (_: IOException) {
                return@launch
            }
            ensureActive()
            publishSong(stationId, enriched)
        }
    }

    private fun preferredSong(): SongInfo? {
        val fromStation = stationSong ?: return streamSong
        val fromStream = streamSong ?: return fromStation
        if (fromStation.title.equals(fromStream.title, ignoreCase = true) &&
            fromStation.artist.equals(fromStream.artist, ignoreCase = true)
        ) {
            return if (fromStation.artworkUrl == null && fromStream.artworkData != null) {
                fromStation.copy(artworkData = fromStream.artworkData)
            } else {
                fromStation
            }
        }
        return if (!stationSongAvailable && streamSongChangedSinceStationSong) fromStream else fromStation
    }

    private fun publishSong(stationId: String, song: SongInfo?) {
        if (currentSong == song) return
        currentSong = song
        publish(stationId, song)
    }

    private companion object {
        val pollDelaysMs = listOf(10_000L, 30_000L, 60_000L, 10_000L, 30_000L, 60_000L, 30_000L, 10_000L)
    }
}

private const val REQUEST_TIMEOUT_MS = 5_000
private const val MAX_BODY_BYTES = 64 * 1024

internal suspend fun fetchMetadata(
    url: String,
    userAgent: String? = null,
    maxBodyBytes: Int = MAX_BODY_BYTES
): String? {
    val connection = URL(url).openConnection() as? HttpURLConnection ?: return null
    try {
        connection.connectTimeout = REQUEST_TIMEOUT_MS
        connection.readTimeout = REQUEST_TIMEOUT_MS
        userAgent?.let { connection.setRequestProperty("User-Agent", it) }
        if (connection.responseCode !in 200..299 || connection.contentLength > maxBodyBytes) {
            return null
        }
        val context = currentCoroutineContext()
        return connection.inputStream.use { input ->
            val body = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                context.ensureActive()
                val count = input.read(buffer)
                if (count == -1) break
                if (body.size() + count > maxBodyBytes) return null
                body.write(buffer, 0, count)
            }
            String(body.toByteArray(), Charsets.UTF_8)
        }
    } finally {
        connection.disconnect()
    }
}
