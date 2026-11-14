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
    private val trace: (String) -> Unit = {},
    private val search: suspend (String) -> String? = { url ->
        withContext(Dispatchers.IO) { fetchMetadata(url, MUSICBRAINZ_USER_AGENT, 512 * 1024, trace) }
    },
    private val hasCover: suspend (String) -> Boolean = { hasCoverArt(it, trace) },
) {
    private val mutex = Mutex()
    private val cache = LinkedHashMap<Pair<String, String>, Match>(64, 0.75f, true)
    private var lastSearchAt: Long? = null

    private class Match(val spelling: SongInfo?, val coverUrls: List<String>) {
        var coverChecked = false
        var coverUrl: String? = null
    }

    private data class SearchMatches(
        val result: JsonObject,
        val matches: List<Pair<JsonObject, SongInfo>>,
        val relaxedSpelling: Boolean = false,
    )

    private data class ArtistAlias(val id: String, val name: String)

    suspend fun enrich(song: SongInfo): SongInfo {
        val artistName = song.artist ?: return song.also { trace("match skipped: missing artist") }
        val artist = artistName.artistSearchText().normalizedSongText().takeIf { it.isNotEmpty() } ?: return song
        val title = song.title.normalizedSongText().takeIf { it.isNotEmpty() } ?: return song
        val key = artistName.artistSearchText().spellingKey().lowercase(Locale.ROOT) to song.title.spellingKey().lowercase(Locale.ROOT)
        return mutex.withLock {
            val cached = cache[key]
            if (cached != null) trace("match cache title=${song.title.take(120)} artist=${artistName.take(120)}")
            val match = cached ?: run {
                trace("search title=${song.title.take(120)} artist=${artistName.take(120)}")
                val result = findRecordings(song.title, artistName) ?: return@withLock song.also { trace("search returned no JSON object") }
                if (result["recordings"] !is JsonArray) return@withLock song.also { trace("search response has no recordings") }
                var selected = SearchMatches(result, exactMatches(result, title, artist))
                if (selected.matches.isEmpty()) {
                    selected = findLeadingArticle(result, title, artist) ?: selected
                }
                if (selected.matches.isEmpty()) {
                    selected = findTitleVariant(song.title, artistName) ?: selected
                }
                if (selected.matches.isEmpty()) {
                    selected = findMissingApostrophe(song.title, artistName) ?: selected
                }
                if (selected.matches.isEmpty()) {
                    selected = findArtistAlias(song.title, artistName) ?: selected
                }
                val recordings = selected.result["recordings"] as? JsonArray ?: return@withLock song
                val matches = selected.matches
                val count = (selected.result["count"] as? JsonPrimitive)?.intOrNull
                trace("search results total=$count returned=${recordings.size} exact=${matches.size}")
                val candidates = matches.map { it.second }.filter { candidate ->
                    selected.relaxedSpelling ||
                        (candidate.artist?.artistSearchText()?.spellingKey()?.lowercase(Locale.ROOT) == key.first &&
                            candidate.title.spellingKey().lowercase(Locale.ROOT) == key.second)
                }
                val spellings = candidates.groupBy { candidate ->
                    candidate.title.spellingKey() to candidate.artist?.artistSearchText()?.spellingKey()
                }
                val spelling = spellings.values.maxByOrNull { it.size }
                    ?.takeIf { group ->
                        (count == recordings.size && spellings.size == 1) ||
                            (count != null && group.size >= 2 && group.size * 2 > candidates.size)
                    }?.firstOrNull()
                val coverUrls = coverUrls(matches.map { it.first })
                trace("match spelling=${spelling?.displayTitle?.take(120) ?: "none"} coverCandidates=${coverUrls.size}${if (spelling == null && count != recordings.size) " reason=incomplete or conflicting results" else ""}")
                Match(spelling, coverUrls).also {
                    cache[key] = it
                    if (cache.size > 64) cache.remove(cache.keys.first())
                }
            }
            val enriched = match.spelling?.let { song.copy(title = it.title, artist = it.artist) } ?: song
            if (song.artworkUrl != null || song.artworkData != null) return@withLock enriched.also { trace("cover skipped: station or stream supplied art") }

            if (!match.coverChecked) {
                match.coverUrl = try {
                    match.coverUrls.firstOrNull { url ->
                        hasCover(url).also { trace("cover candidate $url available=$it") }
                    }
                } catch (error: IOException) {
                    trace("cover check error=${error.javaClass.simpleName}: ${error.message}")
                    return@withLock enriched
                }
                match.coverChecked = true
            }
            trace("cover result=${match.coverUrl ?: "none"}")
            match.coverUrl?.let { enriched.copy(artworkUrl = it) } ?: enriched
        }
    }

    private fun exactMatches(result: JsonObject, title: String, artist: String): List<Pair<JsonObject, SongInfo>> =
        (result["recordings"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().mapNotNull { recording ->
            val recordedTitle = recording.stringOrNull("title")?.usableSongText()
            val recordedArtist = recording.creditedArtist().usableSongText()
            if (recordedTitle?.normalizedSongText() != title ||
                recordedArtist?.artistSearchText()?.normalizedSongText() != artist
            ) return@mapNotNull null
            recording to SongInfo(recordedTitle, recordedArtist)
        }

    private fun findLeadingArticle(result: JsonObject, title: String, artist: String): SearchMatches? {
        val matches = (result["recordings"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().mapNotNull { recording ->
            val recordedTitle = recording.stringOrNull("title")?.usableSongText() ?: return@mapNotNull null
            val recordedArtist = recording.creditedArtist().usableSongText() ?: return@mapNotNull null
            val creditedName = recordedArtist.artistSearchText().normalizedSongText()
            if (recordedTitle.normalizedSongText() != title ||
                !creditedName.startsWith("the ") || creditedName.removePrefix("the ") != artist
            ) return@mapNotNull null
            recording to SongInfo(recordedTitle, recordedArtist)
        }
        if (matches.isEmpty()) return null
        trace("leading article matches=${matches.size}")
        return SearchMatches(result, matches, relaxedSpelling = true)
    }

    private suspend fun findMissingApostrophe(title: String, artist: String): SearchMatches? {
        val corrected = missingApostrophePattern.replace(title) { match ->
            val replacement = contractions[match.value.lowercase(Locale.ROOT)] ?: match.value
            when {
                match.value == match.value.uppercase(Locale.ROOT) -> replacement.uppercase(Locale.ROOT)
                match.value.first().isUpperCase() -> replacement.replaceFirstChar { it.uppercase(Locale.ROOT) }
                else -> replacement
            }
        }
        if (corrected == title) return null
        val result = findRecordings(corrected, artist) ?: return null
        val titleKey = title.normalizedSongText().replace(" ", "")
        val artistKey = artist.artistSearchText().normalizedSongText()
        val matches = (result["recordings"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().mapNotNull { recording ->
            val recordedTitle = recording.stringOrNull("title")?.usableSongText() ?: return@mapNotNull null
            val recordedArtist = recording.creditedArtist().usableSongText() ?: return@mapNotNull null
            if (recordedTitle.normalizedSongText().replace(" ", "") != titleKey ||
                recordedArtist.artistSearchText().normalizedSongText() != artistKey
            ) return@mapNotNull null
            recording to SongInfo(recordedTitle, recordedArtist)
        }
        trace("apostrophe search title=${corrected.take(120)} matches=${matches.size}")
        return SearchMatches(result, matches, relaxedSpelling = true)
    }

    private suspend fun findTitleVariant(title: String, artist: String): SearchMatches? {
        val feature = featuredSuffix.find(title) ?: return null
        val comparable = title.removeRange(feature.range).trim()
        val baseTitle = comparable.substringBefore(" (")
        val guest = feature.groupValues[1].normalizedSongText()
        val artistKey = artist.artistSearchText().normalizedSongText()
        val comparableKey = comparable.normalizedSongText()
        val result = findRecordings(baseTitle, artist) ?: return null
        val matches = (result["recordings"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().mapNotNull { recording ->
            val recordedTitle = recording.stringOrNull("title")?.usableSongText() ?: return@mapNotNull null
            if (recordedTitle.substringBefore(" (").normalizedSongText() != baseTitle.normalizedSongText() ||
                !comparableKey.isOneEditFrom(recordedTitle.normalizedSongText())
            ) return@mapNotNull null
            val credits = (recording["artist-credit"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
            val artistMatches = credits.size == 2 &&
                credits[0].stringOrNull("name")?.artistSearchText()?.normalizedSongText() == artistKey &&
                credits[1].stringOrNull("name")?.normalizedSongText() == guest &&
                credits[0].stringOrNull("joinphrase")?.trim()?.matches(featureJoin) == true
            if (!artistMatches) return@mapNotNull null
            recording to SongInfo(recordedTitle, recording.creditedArtist())
        }
        trace("title variant search base=${baseTitle.take(120)} matches=${matches.size}")
        return SearchMatches(result, matches, relaxedSpelling = true)
    }

    private suspend fun findArtistAlias(title: String, artist: String): SearchMatches? {
        val query = URLEncoder.encode("alias:${artist.artistSearchText().searchPhrase()}", "UTF-8")
        val result = requestJson("https://musicbrainz.org/ws/2/artist/?query=$query&fmt=json&limit=10") ?: return null
        val artists = result["artists"] as? JsonArray ?: return null
        if ((result["count"] as? JsonPrimitive)?.intOrNull != artists.size) return null
        val artistKey = artist.artistSearchText().normalizedSongText()
        val aliases = artists.filterIsInstance<JsonObject>().flatMap { candidate ->
            val id = candidate.stringOrNull("id")?.takeIf(releaseId::matches) ?: return@flatMap emptyList()
            (candidate["aliases"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().mapNotNull { alias ->
                alias.stringOrNull("name")?.usableSongText()
                    ?.takeIf { it.artistSearchText().normalizedSongText() == artistKey }
                    ?.let { ArtistAlias(id, it) }
            }
        }.distinctBy { it.id }
        val alias = aliases.singleOrNull() ?: return null
        val recordingResult = findRecordings(title, artist, alias.id) ?: return null
        val titleKey = title.normalizedSongText()
        val matches = (recordingResult["recordings"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().mapNotNull { recording ->
            val recordedTitle = recording.stringOrNull("title")?.usableSongText() ?: return@mapNotNull null
            val credits = (recording["artist-credit"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
            if (recordedTitle.normalizedSongText() != titleKey || credits.size != 1 ||
                credits[0]["artist"] !is JsonObject ||
                (credits[0]["artist"] as JsonObject).stringOrNull("id") != alias.id
            ) return@mapNotNull null
            recording to SongInfo(recordedTitle, alias.name)
        }
        trace("artist alias search artist=${artist.take(120)} matches=${matches.size}")
        return SearchMatches(recordingResult, matches)
    }

    private suspend fun findRecordings(title: String, artist: String, artistId: String? = null): JsonObject? {
        val artistTerm = if (artistId == null) "artist:${artist.artistSearchText().searchPhrase()}" else "arid:$artistId"
        val query = URLEncoder.encode("recording:${title.searchPhrase()} AND $artistTerm", "UTF-8")
        return requestJson("https://musicbrainz.org/ws/2/recording/?query=$query&fmt=json&limit=10")
    }

    private suspend fun requestJson(url: String): JsonObject? {
        waitForSearchSlot()
        val response = try {
            search(url)
        } catch (error: IOException) {
            trace("search error=${error.javaClass.simpleName}: ${error.message}; retrying")
            waitForSearchSlot()
            search(url)
        }
        return response?.let(::parseObject)
    }

    private fun String.isOneEditFrom(other: String): Boolean {
        if (kotlin.math.abs(length - other.length) > 1) return false
        var left = 0
        var right = 0
        var edits = 0
        while (left < length && right < other.length) {
            if (this[left] == other[right]) {
                left++
                right++
            } else {
                if (++edits > 1) return false
                if (length >= other.length) left++
                if (other.length >= length) right++
            }
        }
        return edits + (length - left) + (other.length - right) <= 1
    }

    private suspend fun waitForSearchSlot() {
        lastSearchAt?.let {
            val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - it)
            delay((1_100L - elapsed).coerceAtLeast(0))
        }
        lastSearchAt = System.nanoTime()
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

    private fun String.artistSearchText(): String =
        replace(slashSpacing, "/").replace(spanishAnd, " & ")

    private fun String.spellingKey(): String = Normalizer.normalize(
        replace('’', '\'').replace(slashSpacing, "/"), Normalizer.Form.NFC
    )

    private companion object {
        val releaseId = Regex("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")
        val slashSpacing = Regex("\\s*/\\s*")
        val spanishAnd = Regex("\\s+[yY]\\s+")
        val featuredSuffix = Regex("\\s*\\((?:feat\\.?|ft\\.?|featuring)\\s+([^()]+)\\)\\s*$", RegexOption.IGNORE_CASE)
        val featureJoin = Regex("(?:feat\\.?|ft\\.?|featuring)", RegexOption.IGNORE_CASE)
        val contractions = mapOf(
            "youre" to "you're", "youve" to "you've", "dont" to "don't",
            "cant" to "can't", "wont" to "won't", "im" to "i'm",
            "ive" to "i've", "theyre" to "they're", "weve" to "we've",
            "didnt" to "didn't", "doesnt" to "doesn't", "isnt" to "isn't",
            "arent" to "aren't", "wasnt" to "wasn't", "werent" to "weren't",
            "couldnt" to "couldn't", "wouldnt" to "wouldn't", "shouldnt" to "shouldn't"
        )
        val missingApostrophePattern = Regex("\\b(?:${contractions.keys.joinToString("|")})\\b", RegexOption.IGNORE_CASE)
    }
}

private const val MUSICBRAINZ_USER_AGENT = "IPRadio/1.0 (https://github.com/teodory/IPRadio)"

private suspend fun hasCoverArt(url: String, trace: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "HEAD"
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.setRequestProperty("User-Agent", MUSICBRAINZ_USER_AGENT)
        when (val status = connection.responseCode) {
            in 200..299 -> true.also { trace("HEAD $url HTTP $status") }
            in 300..399 -> (connection.getHeaderField("Location") != null).also { trace("HEAD $url HTTP $status redirect=${it}") }
            404 -> false.also { trace("HEAD $url HTTP $status") }
            else -> throw IOException("Cover Art Archive returned HTTP $status")
        }
    } finally {
        connection.disconnect()
    }
}
