package com.example.ipradio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

internal class MusicBrainzArtwork(
    private val search: suspend (String) -> String? = { url ->
        withContext(Dispatchers.IO) { fetchMetadata(url, MUSICBRAINZ_USER_AGENT, 256 * 1024) }
    },
    private val hasCover: suspend (String) -> Boolean = ::hasCoverArt,
) {
    private val mutex = Mutex()
    private val cache = LinkedHashMap<Pair<String, String>, String?>(64, 0.75f, true)
    private var lastSearchAt: Long? = null

    suspend fun find(song: SongInfo): String? {
        val artist = song.artist?.normalizedSongText()?.takeIf { it.isNotEmpty() } ?: return null
        val title = song.title.normalizedSongText().takeIf { it.isNotEmpty() } ?: return null
        val key = artist to title
        return mutex.withLock {
            if (cache.containsKey(key)) return@withLock cache[key]

            lastSearchAt?.let {
                val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - it)
                delay((1_100L - elapsed).coerceAtLeast(0))
            }
            lastSearchAt = System.nanoTime()
            val query = URLEncoder.encode("recording:\"$title\" AND artist:\"$artist\"", "UTF-8")
            val result = search("https://musicbrainz.org/ws/2/recording/?query=$query&fmt=json&limit=5")
                ?.let(::parseObject) ?: return@withLock null
            val releases = (result["recordings"] as? JsonArray).orEmpty()
                .filterIsInstance<JsonObject>()
                .filter { it.stringOrNull("title")?.normalizedSongText() == title && it.creditedArtist() == artist }
                .flatMap { (it["releases"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>() }
                .sortedBy { release ->
                    val group = release["release-group"] as? JsonObject
                    when {
                        release.stringOrNull("status") == "Bootleg" -> 2
                        group?.stringOrNull("primary-type") == "Album" &&
                            (group["secondary-types"] as? JsonArray).isNullOrEmpty() -> 0
                        else -> 1
                    }
                }
                .mapNotNull { it.stringOrNull("id")?.takeIf(releaseId::matches) }
                .distinct()
                .take(5)

            val artwork = releases.map { "https://coverartarchive.org/release/$it/front-250" }
                .firstOrNull { hasCover(it) }
            cache[key] = artwork
            if (cache.size > 64) cache.remove(cache.keys.first())
            artwork
        }
    }

    private fun JsonObject.creditedArtist(): String =
        (get("artist-credit") as? JsonArray).orEmpty()
            .filterIsInstance<JsonObject>()
            .joinToString("") { credit ->
                val name = credit.stringOrNull("name") ?: (credit["artist"] as? JsonObject)?.stringOrNull("name")
                name.orEmpty() + credit.stringOrNull("joinphrase").orEmpty()
            }.normalizedSongText()

    private companion object {
        val releaseId = Regex("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")
    }
}

private const val MUSICBRAINZ_USER_AGENT = "IPRadio/1.0 (https://github.com/teodory/IPRadio)"

private suspend fun hasCoverArt(url: String): Boolean = withContext(Dispatchers.IO) {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "HEAD"
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.setRequestProperty("User-Agent", MUSICBRAINZ_USER_AGENT)
        when (val status = connection.responseCode) {
            in 200..299 -> true
            in 300..399 -> connection.getHeaderField("Location") != null
            404 -> false
            else -> throw IOException("Cover Art Archive returned HTTP $status")
        }
    } finally {
        connection.disconnect()
    }
}
