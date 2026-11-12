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
    private val fetch: suspend (String) -> String? = ::fetchMetadata,
    private val waitForNextPoll: suspend (Long) -> Unit = { delay(it) },
    private val publish: (stationId: String, title: String?) -> Unit,
) {
    private data class Source(val stationId: String, val url: String, val format: MetadataFormat)

    private var source: Source? = null
    private var pollJob: Job? = null

    // Called on the playback scope's dispatcher, which also receives title updates.
    fun setStation(station: Station?) {
        val format = station?.metadataFormat
        val nextSource = if (station != null && format != null && station.infoDataUrl.isNotBlank()) {
            Source(station.id, station.infoDataUrl, format)
        } else {
            null
        }
        if (source == nextSource) return
        source = nextSource
        pollJob?.cancel()
        pollJob = null

        if (nextSource == null) return
        pollJob = scope.launch {
            var lastTitle: String? = null
            var delayIndex = 0
            while (isActive) {
                val title = try {
                    withContext(Dispatchers.IO) {
                        fetch(nextSource.url)?.let { parseSongTitle(nextSource.format, it) }
                    }
                } catch (_: IOException) {
                    null
                }
                ensureActive()
                if (title != null && title != lastTitle) {
                    lastTitle = title
                    publish(nextSource.stationId, title)
                    delayIndex = 0
                }
                waitForNextPoll(pollDelaysMs[delayIndex])
                delayIndex = (delayIndex + 1).coerceAtMost(pollDelaysMs.lastIndex)
            }
        }
    }

    private companion object {
        val pollDelaysMs = listOf(10_000L, 30_000L, 60_000L, 10_000L, 30_000L, 60_000L, 30_000L, 10_000L)
    }
}

private const val REQUEST_TIMEOUT_MS = 5_000
private const val MAX_BODY_BYTES = 64 * 1024

private suspend fun fetchMetadata(url: String): String? {
    val connection = URL(url).openConnection() as? HttpURLConnection ?: return null
    try {
        connection.connectTimeout = REQUEST_TIMEOUT_MS
        connection.readTimeout = REQUEST_TIMEOUT_MS
        if (connection.responseCode !in 200..299 || connection.contentLength > MAX_BODY_BYTES) {
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
                if (body.size() + count > MAX_BODY_BYTES) return null
                body.write(buffer, 0, count)
            }
            String(body.toByteArray(), Charsets.UTF_8)
        }
    } finally {
        connection.disconnect()
    }
}
