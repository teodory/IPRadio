package com.example.ipradio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit

internal class MusicBrainzLookup(
    private val search: suspend (String) -> String? = { url ->
        withContext(Dispatchers.IO) { fetchMetadata(url, MUSICBRAINZ_USER_AGENT, 256 * 1024) }
    },
    private val hasCover: suspend (String) -> Boolean = ::hasCoverArt,
) {
    private val mutex = Mutex()
    private val cache = LinkedHashMap<Pair<String, String>, Match>(64, 0.75f, true)
    private var lastSearchAt: Long? = null

    private class Match(val spelling: SongInfo?, val coverUrls: List<String>) {
        var coverChecked = false
        var coverUrl: String? = null
    }

    suspend fun enrich(song: SongInfo): SongInfo {
        val artistName = song.artist ?: return song
        val artist = artistName.normalizedSongText().takeIf { it.isNotEmpty() } ?: return song
        val title = song.title.normalizedSongText().takeIf { it.isNotEmpty() } ?: return song
        val key = artistName.spellingKey().lowercase(Locale.ROOT) to song.title.spellingKey().lowercase(Locale.ROOT)
        return mutex.withLock {
            val match = cache[key] ?: run {
                val result = findRecordings(song.title, artistName) ?: return@withLock song
                val recordings = result["recordings"] as? JsonArray ?: return@withLock song
                val matches = recordings.filterIsInstance<JsonObject>().mapNotNull { recording ->
                    val recordedTitle = recording.stringOrNull("title")?.usableSongText()
                    val recordedArtist = recording.creditedArtist().usableSongText()
                    if (recordedTitle?.normalizedSongText() != title ||
                        recordedArtist?.normalizedSongText() != artist
                    ) return@mapNotNull null
                    recording to SongInfo(recordedTitle, recordedArtist)
                }
                val count = (result["count"] as? JsonPrimitive)?.intOrNull
                val spelling = if (count == recordings.size) {
                    matches.map { it.second }
                        .filter { candidate ->
                            candidate.artist?.spellingKey()?.lowercase(Locale.ROOT) == key.first &&
                                candidate.title.spellingKey().lowercase(Locale.ROOT) == key.second
                        }
                        .distinctBy { candidate -> candidate.title.spellingKey() to candidate.artist?.spellingKey() }
                        .singleOrNull()
                } else null
                Match(spelling, coverUrls(matches.map { it.first })).also {
                    cache[key] = it
                    if (cache.size > 64) cache.remove(cache.keys.first())
                }
            }
            val enriched = match.spelling?.let { song.copy(title = it.title, artist = it.artist) } ?: song
            if (song.artworkUrl != null || song.artworkData != null) return@withLock enriched

            if (!match.coverChecked) {
                match.coverUrl = try {
                    match.coverUrls.firstOrNull { hasCover(it) }
                } catch (_: IOException) {
                    return@withLock enriched
                }
                match.coverChecked = true
            }
            match.coverUrl?.let { enriched.copy(artworkUrl = it) } ?: enriched
        }
    }

    private suspend fun findRecordings(title: String, artist: String): JsonObject? {
        lastSearchAt?.let {
            val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - it)
            delay((1_100L - elapsed).coerceAtLeast(0))
        }
        lastSearchAt = System.nanoTime()
        val query = URLEncoder.encode("recording:${title.searchPhrase()} AND artist:${artist.searchPhrase()}", "UTF-8")
        return search("https://musicbrainz.org/ws/2/recording/?query=$query&fmt=json&limit=10")
            ?.let(::parseObject)
    }

    private fun coverUrls(recordings: List<JsonObject>): List<String> = recordings
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
        .map { "https://coverartarchive.org/release/$it/front-250" }

    private fun JsonObject.creditedArtist(): String =
        (get("artist-credit") as? JsonArray).orEmpty()
            .filterIsInstance<JsonObject>()
            .joinToString("") { credit ->
                val name = credit.stringOrNull("name") ?: (credit["artist"] as? JsonObject)?.stringOrNull("name")
                name.orEmpty() + credit.stringOrNull("joinphrase").orEmpty()
            }

    private fun String.searchPhrase(): String =
        "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

    private fun String.spellingKey(): String = Normalizer.normalize(replace('’', '\''), Normalizer.Form.NFC)

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
