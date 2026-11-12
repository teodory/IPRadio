package com.example.ipradio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption
import java.util.UUID

class StationCatalog internal constructor(private val file: File) {
    private val mutex = Mutex()
    private val loaded = try {
        Result.success(readStations())
    } catch (error: Exception) {
        Result.failure(error)
    }
    private val state = MutableStateFlow(loaded.getOrDefault(defaultStations))
    val stations: StateFlow<List<Station>> = state.asStateFlow()

    suspend fun save(
        id: String?,
        name: String,
        url: String,
        image: String,
        infoDataUrl: String
    ): Result<Station> = updateStations { current ->
        val previous = id?.let { stationId ->
            current.firstOrNull { it.id == stationId }
                ?: throw IllegalArgumentException("The station no longer exists.")
        }
        val station = validatedStation(
            previous?.id ?: UUID.randomUUID().toString(),
            name, url, image, infoDataUrl, defaultStationsById[id]
        )
        val updated = if (previous == null) {
            current + station
        } else {
            current.map { if (it.id == station.id) station else it }
        }
        updated to station
    }

    internal suspend fun move(id: String, offset: Int): Result<Boolean> = updateStations { current ->
        require(offset == -1 || offset == 1)
        val from = current.indexOfFirst { it.id == id }
        require(from >= 0) { "The station no longer exists." }
        val to = from + offset
        val updated = if (to in current.indices) {
            current.toMutableList().apply { add(to, removeAt(from)) }
        } else {
            current
        }
        updated to (updated !== current)
    }

    private suspend fun <T> updateStations(
        transform: (List<Station>) -> Pair<List<Station>, T>
    ): Result<T> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                loaded.exceptionOrNull()?.let {
                    throw IOException("The saved station catalog could not be read.", it)
                }
                val current = state.value
                val (updated, result) = transform(current)
                if (updated !== current) {
                    writeStations(updated)
                    state.value = updated
                }
                Result.success(result)
            } catch (error: IOException) {
                Result.failure(error)
            } catch (error: IllegalArgumentException) {
                Result.failure(error)
            } catch (error: SecurityException) {
                Result.failure(error)
            }
        }
    }

    private fun readStations(): List<Station> {
        val contents = try {
            Files.readAllBytes(file.toPath()).decodeToString(throwOnInvalidSequence = true)
        } catch (_: NoSuchFileException) {
            return defaultStations
        }
        val stations = Json.parseToJsonElement(contents).jsonArray.map { entry ->
            val fields = entry.jsonObject
            val id = fields.string("id")
            require(id.isNotBlank())
            val builtIn = defaultStationsById[id]
            val savedUrl = fields.string("url")
            val url = if (builtIn != null && savedUrl == previousDefaultStreams[id]) builtIn.url else savedUrl
            validatedStation(id, fields.string("name"), url, fields.string("image"), fields.string("infoDataUrl"), builtIn)
        }
        val ids = stations.map(Station::id).toSet()
        require(stations.isNotEmpty() && ids.size == stations.size)
        return stations + defaultStations.filter { it.id !in ids }
    }

    private fun writeStations(stations: List<Station>) {
        val contents = JsonArray(stations.map { station ->
            buildJsonObject {
                put("id", station.id)
                put("name", station.name)
                put("url", station.url)
                put("image", station.image)
                put("infoDataUrl", station.infoDataUrl)
            }
        }).toString()

        val temporary = File.createTempFile("stations-", ".tmp", file.absoluteFile.parentFile)
        try {
            temporary.outputStream().use {
                it.write(contents.encodeToByteArray())
                it.fd.sync()
            }
            Files.move(
                temporary.toPath(), file.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            temporary.delete()
        }
    }

    companion object {
        @Volatile
        private var instance: StationCatalog? = null

        fun getInstance(context: Context): StationCatalog = instance ?: synchronized(this) {
            instance ?: StationCatalog(File(context.applicationContext.filesDir, "stations.json"))
                .also { instance = it }
        }
    }
}

private fun JsonObject.string(key: String): String {
    val field = this[key]
    require(field is JsonPrimitive && field.isString)
    return field.content
}

private val webSchemes = setOf("http", "https")
private val streamSchemes = webSchemes + setOf("rtsp", "udp")
private val previousDefaultStreams = mapOf(
    "rockfm" to "https://rockfm-cope.flumotion.com/chunks.m3u8",
    "fmplus" to "http://193.108.24.21:8000/fmplus?file=.mp3"
)
private val previousDefaultArtwork = mapOf(
    "fmplus" to "http://fmplus.net/playernew/i/logo_fmplus.png",
    "tangra-mega-rock" to "http://www.radiotangra.com/img/logo_tangra.png"
)

private fun validatedStation(
    id: String,
    name: String,
    url: String,
    image: String,
    infoDataUrl: String,
    builtIn: Station?
): Station {
    val trimmedName = name.trim()
    require(trimmedName.isNotEmpty()) { "Enter a station name." }
    val streamUrl = validateUrl(url, streamSchemes, "Enter a valid HTTP, HTTPS, RTSP, or UDP stream URL.")
    val imageUrl = validateUrl(image, webSchemes, "Enter a valid HTTP or HTTPS image URL.", optional = true)
    val metadataUrl = validateUrl(
        infoDataUrl, webSchemes, "Enter a valid HTTP or HTTPS metadata URL.", optional = true
    )
    return Station(
        id = id,
        name = trimmedName,
        url = streamUrl,
        image = if (builtIn != null && imageUrl == previousDefaultArtwork[id]) builtIn.image else imageUrl,
        logo = builtIn?.logo ?: 0,
        infoDataUrl = metadataUrl,
        metadataFormat = builtIn?.metadataFormat ?: MetadataFormat.PLAIN_TEXT.takeIf { metadataUrl.isNotEmpty() }
    )
}

private fun validateUrl(
    value: String,
    schemes: Set<String>,
    error: String,
    optional: Boolean = false
): String {
    val trimmed = value.trim()
    if (optional && trimmed.isEmpty()) return trimmed

    val uri = try {
        URI(trimmed)
    } catch (cause: URISyntaxException) {
        throw IllegalArgumentException(error, cause)
    }
    val scheme = uri.scheme?.lowercase()
    val validPort = uri.port in 1..65535 || (uri.port == -1 && scheme != "udp")
    require(scheme in schemes && uri.host != null && validPort) {
        error
    }
    return scheme + trimmed.substring(uri.scheme.length)
}
