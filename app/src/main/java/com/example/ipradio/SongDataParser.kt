package com.example.ipradio

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun parseSongTitle(format: MetadataFormat, body: String): String? {
    val payload = body.trim().removePrefix("\uFEFF").trim()
    if (format == MetadataFormat.PLAIN_TEXT) {
        if (payload.startsWith('{') || payload.startsWith('[')) return null
        return payload.usableSongText()
    }

    // Some Flumotion responses have a trailing percent marker after the JSON object.
    val json = if (format == MetadataFormat.FLUMOTION) payload.removeSuffix("%") else payload
    val root = parseObject(json) ?: return null
    return when (format) {
        MetadataFormat.ZROCK -> (root["main_slider"] as? JsonObject)
            ?.let { it["now"] as? JsonObject }
            ?.songTitle("artist", "title")
        MetadataFormat.METACAST -> ((root["nowplaying"] as? JsonArray)?.firstOrNull() as? JsonObject)
            ?.songTitle("artist", "title")
        MetadataFormat.MAGIC -> root.songTitle("performer", "name")
        MetadataFormat.FLUMOTION -> root.string("value")
            ?.let(::parseObject)
            ?.songTitle("author", "title")
        MetadataFormat.PLAIN_TEXT -> null
    }
}

private fun parseObject(value: String): JsonObject? = try {
    Json.parseToJsonElement(value) as? JsonObject
} catch (_: SerializationException) {
    null
} catch (_: StackOverflowError) {
    // The JSON reader can exhaust the stack on a deeply nested response.
    null
}

private fun JsonObject.songTitle(artistKey: String, titleKey: String): String? {
    val title = string(titleKey)?.usableSongText() ?: return null
    val artist = string(artistKey)?.usableSongText()
    return if (artist == null) title else "$artist - $title"
}

private fun JsonObject.string(key: String): String? =
    (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

private val htmlTag = Regex("<\\s*[/!?a-zA-Z][^<>]*>")

private fun String.usableSongText(): String? {
    val text = trim().removePrefix("\uFEFF").trim()
    return text.takeIf {
        it.isNotEmpty() && !it.any(Char::isISOControl) && !htmlTag.containsMatchIn(it) &&
            !it.equals("null", ignoreCase = true) && !it.equals("null - null", ignoreCase = true)
    }
}
