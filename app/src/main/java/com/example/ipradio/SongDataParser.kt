package com.example.ipradio

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI
import java.net.URISyntaxException
import java.text.Normalizer
import java.util.Locale

internal data class SongInfo(
    val title: String,
    val artist: String? = null,
    val artworkUrl: String? = null,
    val artworkData: ByteArray? = null
) {
    val displayTitle: String
        get() = artist?.let { "$it - $title" } ?: title
}

internal fun parseSong(format: MetadataFormat, body: String, sourceUrl: String = ""): SongInfo? {
    val payload = body.trim().removePrefix("\uFEFF").trim()
    if (format == MetadataFormat.PLAIN_TEXT) {
        if (payload.startsWith('{') || payload.startsWith('[')) return null
        return parseStreamSong(payload)
    }

    // Some Flumotion responses have a trailing percent marker after the JSON object.
    val json = if (format == MetadataFormat.FLUMOTION) payload.removeSuffix("%") else payload
    val root = parseObject(json) ?: return null
    return when (format) {
        MetadataFormat.ZROCK -> (root["main_slider"] as? JsonObject)
            ?.let { it["now"] as? JsonObject }
            ?.let { now ->
                // The live feed's imported rows have the artist in the BOM-prefixed title field.
                if (now.stringOrNull("title")?.startsWith('\uFEFF') == true && !now.stringOrNull("artist").isNullOrBlank()) {
                    now.song("title", "artist", "image", sourceUrl)
                } else {
                    now.song("artist", "title", "image", sourceUrl)
                }
            }
        MetadataFormat.METACAST -> ((root["nowplaying"] as? JsonArray)?.firstOrNull() as? JsonObject)
            ?.takeUnless { it.stringOrNull("status") == "history" }
            ?.song("artist", "title", "imageUrl", sourceUrl)
        MetadataFormat.MAGIC -> root.song("performer", "name", "img", sourceUrl)
        MetadataFormat.FLUMOTION -> root.stringOrNull("value")
            ?.let(::parseObject)
            ?.song("author", "title", "image", sourceUrl)
        MetadataFormat.PLAIN_TEXT -> null
    }
}

internal fun parseStreamSong(title: String?, artist: String? = null, artworkData: ByteArray? = null): SongInfo? {
    val text = title?.usableSongText() ?: return null
    val image = artworkData?.takeIf { it.size in 1..256 * 1024 }
    artist?.usableSongText()?.let { return SongInfo(text, it, artworkData = image) }

    val parts = trackSeparator.split(text, limit = 2)
    val performer = parts.first().usableSongText()
    val track = parts.getOrNull(1)?.usableSongText()
    return if (performer != null && track != null) SongInfo(track, performer, artworkData = image)
    else SongInfo(text, artworkData = image)
}

internal fun parseObject(value: String): JsonObject? = try {
    Json.parseToJsonElement(value) as? JsonObject
} catch (_: SerializationException) {
    null
} catch (_: StackOverflowError) {
    // The JSON reader can exhaust the stack on a deeply nested response.
    null
}

private fun JsonObject.song(artistKey: String, titleKey: String, imageKey: String, sourceUrl: String): SongInfo? {
    val title = stringOrNull(titleKey)?.usableSongText() ?: return null
    val artist = stringOrNull(artistKey)?.usableSongText()
    return SongInfo(title, artist, stringOrNull(imageKey)?.artworkUrl(sourceUrl))
}

internal fun JsonObject.stringOrNull(key: String): String? =
    (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun String.artworkUrl(sourceUrl: String): String? = try {
    val uri = usableSongText()?.let { URI(sourceUrl).resolve(it) }
    val path = uri?.path.orEmpty()
    uri?.takeIf {
        (it.scheme.equals("http", ignoreCase = true) || it.scheme.equals("https", ignoreCase = true)) &&
            it.host != null && !path.endsWith("/default.svg") && !path.endsWith("/music_top.jpg")
    }?.toString()
} catch (_: URISyntaxException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

private val trackSeparator = Regex("\\s+[-–—]\\s+")
private val htmlTag = Regex("<\\s*[/!?a-zA-Z][^<>]*>")
private val songPunctuation = Regex("[^\\p{L}\\p{N}\\p{M}]+")

internal fun String.normalizedSongText(): String =
    Normalizer.normalize(this, Normalizer.Form.NFC)
        .replace(songPunctuation, " ")
        .trim()
        .lowercase(Locale.ROOT)

private fun String.usableSongText(): String? {
    val text = trim().removePrefix("\uFEFF").trim()
    return text.takeIf {
        it.isNotEmpty() && it != "-" && !it.any(Char::isISOControl) && !htmlTag.containsMatchIn(it) &&
            !it.equals("null", ignoreCase = true) && !it.equals("null - null", ignoreCase = true)
    }
}
