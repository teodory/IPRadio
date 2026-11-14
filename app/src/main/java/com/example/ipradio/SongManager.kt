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
import java.util.concurrent.TimeUnit

internal class SongManager(
    private val scope: CoroutineScope,
    private val trace: (String) -> Unit = {},
    private val fetch: suspend (String) -> String? = { fetchMetadata(it, trace = trace) },
    private val waitForNextPoll: suspend (Long) -> Unit = { delay(it) },
    private val enrichSong: suspend (SongInfo) -> SongInfo = MusicBrainzLookup(trace = trace)::enrich,
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
    private var lastStationEnteredAtMs: Long? = null
    private var lastRapidStationSwitchAtMs: Long? = null
    private var lastLookupStartedAtMs: Long? = null
    var currentSong: SongInfo? = null
        private set

    // Called on the playback scope's dispatcher, which also receives metadata updates.
    fun setStation(station: Station?, poll: Boolean = true) {
        stationName = station?.name
        val nextSource = station?.let {
            Source(it.id, it.url, it.songDataUrl, it.metadataFormat)
        }
        if (source != nextSource) {
            if (nextSource != null && source?.stationId != nextSource.stationId) {
                val now = nowMs()
                if (lastStationEnteredAtMs?.let { now - it < 3_000L } == true) {
                    lastRapidStationSwitchAtMs = now
                }
                lastStationEnteredAtMs = now
            }
            trace("source ${source?.stationId} -> ${nextSource?.stationId} format=${nextSource?.format} url=${nextSource?.infoUrl}")
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
            if (pollJob != null) trace("poll stopped station=${nextSource?.stationId}")
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
                        val body = fetch(nextSource.infoUrl)
                        body?.let { parseSong(nextSource.format, it, nextSource.infoUrl) }.also {
                            trace("poll station=${nextSource.stationId} body=${body?.length ?: "null"} parsed=${it?.displayTitle?.take(120)} art=${it?.artworkUrl != null || it?.artworkData != null}${if (body != null && it == null) " sample=${body.take(120)}" else ""}")
                        }
                    }
                } catch (error: IOException) {
                    trace("poll station=${nextSource.stationId} error=${error.javaClass.simpleName}: ${error.message}")
                    null
                }
                ensureActive()
                val available = song != null && !isStationLabel(song)
                if (song != null && !available) trace("poll station=${nextSource.stationId} ignored station label")
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
        trace("stream station=$stationId song=${next?.displayTitle?.take(120)} art=${next?.artworkData != null}")
        streamSong = next
        streamSongChangedSinceStationSong = true
        updateSong()
    }

    fun setMusicBrainzEnabled(enabled: Boolean) {
        if (musicBrainzEnabled == enabled) return
        musicBrainzEnabled = enabled
        trace("musicbrainz enabled=$enabled")
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
        if (lookupJob?.isActive == true) trace("lookup cancelled station=$stationId")
        lookupJob?.cancel()
        lookupJob = null
        publishSong(stationId, song)
        if (!musicBrainzEnabled || song?.artist == null) {
            trace("lookup skipped station=$stationId reason=${if (!musicBrainzEnabled) "disabled" else "missing artist"}")
            return
        }

        val now = nowMs()
        val recentLookup = lastLookupStartedAtMs?.let { now - it < 3_000L } == true
        val rapidStationSwitch = lastRapidStationSwitchAtMs?.let { now - it < 3_000L } == true
        val waitingForStationMetadata = source?.format != null && source?.infoUrl?.isNotBlank() == true &&
            !stationSongAvailable
        val waitMs = if (recentLookup || rapidStationSwitch || waitingForStationMetadata) 1_000L else 0L
        lookupJob = scope.launch {
            trace("lookup scheduled station=$stationId wait=${waitMs}ms")
            if (waitMs > 0) delay(waitMs)
            var retryDelayMs = 2_000L
            while (isActive) {
                trace("lookup start station=$stationId song=${song.displayTitle.take(120)}")
                lastLookupStartedAtMs = nowMs()
                val enriched = try {
                    enrichSong(song)
                } catch (error: IOException) {
                    trace("lookup station=$stationId error=${error.javaClass.simpleName}: ${error.message}; retry in ${retryDelayMs}ms")
                    delay(retryDelayMs)
                    retryDelayMs = (retryDelayMs * 2).coerceAtMost(60_000L)
                    continue
                }
                ensureActive()
                trace("lookup result station=$stationId song=${enriched.displayTitle.take(120)} art=${enriched.artworkUrl ?: if (enriched.artworkData != null) "embedded" else "none"}")
                publishSong(stationId, enriched)
                break
            }
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
        trace("publish station=$stationId song=${song?.displayTitle?.take(120)} art=${song?.artworkUrl ?: if (song?.artworkData != null) "embedded" else "none"}")
        currentSong = song
        publish(stationId, song)
    }

    private fun nowMs(): Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime())

    private companion object {
        val pollDelaysMs = listOf(10_000L, 30_000L, 60_000L, 10_000L, 30_000L, 60_000L, 30_000L, 10_000L)
    }
}

private const val REQUEST_TIMEOUT_MS = 5_000
private const val MAX_BODY_BYTES = 64 * 1024

internal class MetadataHttpException(val status: Int, url: String) : IOException("HTTP $status from $url")

internal suspend fun fetchMetadata(
    url: String,
    userAgent: String? = null,
    maxBodyBytes: Int = MAX_BODY_BYTES,
    trace: (String) -> Unit = {},
): String? {
    trace("GET $url")
    val connection = URL(url).openConnection() as? HttpURLConnection ?: run {
        trace("GET $url unsupported connection")
        return null
    }
    try {
        connection.connectTimeout = REQUEST_TIMEOUT_MS
        connection.readTimeout = REQUEST_TIMEOUT_MS
        userAgent?.let { connection.setRequestProperty("User-Agent", it) }
        val status = connection.responseCode
        if (status !in 200..299 || connection.contentLength > maxBodyBytes) {
            trace("GET $url HTTP $status contentLength=${connection.contentLength} rejected")
            if (status == 429 || status in 500..599) throw MetadataHttpException(status, url)
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
                if (body.size() + count > maxBodyBytes) {
                    trace("GET $url HTTP $status body too large")
                    return null
                }
                body.write(buffer, 0, count)
            }
            trace("GET $url HTTP $status bytes=${body.size()}")
            String(body.toByteArray(), Charsets.UTF_8)
        }
    } finally {
        connection.disconnect()
    }
}
