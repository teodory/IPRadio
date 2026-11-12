package com.example.ipradio

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

class StationCatalogTest {
    @get:Rule
    val directory = TemporaryFolder()

    @Test
    fun seedsAllStationsInPlaybackOrder() {
        val stations = StationCatalog(storeFile()).stations.value
        assertEquals(
            listOf("ZRock", "1Rock", "Darik", "FM+", "Magic FM", "Tangra Mega Rock",
                "RockFM", "Cadena100", "SoftRockRadio.net", "Melody", "Радио Антена"),
            stations.map { it.name }
        )
        assertEquals(stations.size, stations.map { it.id }.toSet().size)
        assertEquals(stations, StationCatalog(storeFile()).stations.value)
    }

    @Test
    fun renamePreservesIdentityAndMetadataFormatThroughReload() = runBlocking {
        val file = storeFile()
        val catalog = StationCatalog(file)
        val magic = catalog.stations.value.first { it.name == "Magic FM" }
        val edited = catalog.save(
            magic.id, "  1Rock  ", " https://example.org/stream ", " ", magic.infoDataUrl
        ).getOrThrow()

        assertEquals(magic.id, edited.id)
        assertEquals("1Rock", edited.name)
        assertEquals("https://example.org/stream", edited.url)
        assertEquals("", edited.image)
        assertEquals(MetadataFormat.MAGIC, edited.metadataFormat)
        assertEquals(magic.logo, edited.logo)
        val reloaded = StationCatalog(file).stations.value
        assertEquals(catalog.stations.value, reloaded)
        assertEquals(2, reloaded.count { it.name == "1Rock" })
        assertEquals(edited, reloaded.first { it.id == magic.id })
    }

    @Test
    fun concurrentAddsKeepBothStationsAndAssignDistinctIds() = runBlocking {
        val file = storeFile()
        val catalog = StationCatalog(file)
        val initialCount = catalog.stations.value.size
        val (first, second) = listOf("http://example.org/live", "rtsp://example.org/live").map { url ->
            async { catalog.save(null, "RockFM", url, "", "").getOrThrow() }
        }.awaitAll()

        UUID.fromString(first.id)
        UUID.fromString(second.id)
        assertNotEquals(first.id, second.id)
        assertNull(first.metadataFormat)
        assertEquals(0, first.logo)
        val reloaded = StationCatalog(file).stations.value
        assertEquals(initialCount + 2, reloaded.size)
        assertTrue(reloaded.containsAll(listOf(first, second)))
    }

    @Test
    fun acceptsUdpAudioWithAValidPort() = runBlocking {
        val file = storeFile()
        val catalog = StationCatalog(file)
        val streamUrl = "udp://239.0.0.1:5000"
        val station = catalog.save(null, "Station", streamUrl, "", "").getOrThrow()

        assertEquals(streamUrl, StationCatalog(file).stations.value.last().url)
        assertTrue(catalog.save(station.id, station.name, "udp://239.0.0.1", "", "").isFailure)
    }

    @Test
    fun metadataUrlEnablesPlainTextForAStationWithoutABuiltInFormat() = runBlocking {
        val file = storeFile()
        val catalog = StationCatalog(file)
        val station = catalog.save(
            null, "Station", "https://example.org/live", "", "https://example.org/now"
        ).getOrThrow()

        assertEquals(MetadataFormat.PLAIN_TEXT, station.metadataFormat)
        val reloaded = StationCatalog(file)
        assertEquals(station, reloaded.stations.value.last())
        val cleared = reloaded.save(station.id, station.name, station.url, "", "").getOrThrow()
        assertNull(cleared.metadataFormat)
        assertEquals(cleared, StationCatalog(file).stations.value.last())
    }

    @Test
    fun aSavedCatalogKeepsEditsAndGainsMissingBuiltIns() = runBlocking {
        val file = storeFile()
        file.writeText("""
            [
              {"id":"zrock","name":"Renamed","url":"https://example.org/rock","image":"","infoDataUrl":""},
              {"id":"b6500960-4ab0-4dfd-8317-8f5631d47a55","name":"Custom","url":"https://example.org/custom","image":"","infoDataUrl":""}
            ]
        """.trimIndent())

        val catalog = StationCatalog(file)
        val loaded = catalog.stations.value
        assertEquals("Renamed", loaded.first { it.id == "zrock" }.name)
        assertTrue(loaded.any { it.name == "Custom" })
        assertTrue(loaded.any { it.name == "Melody" })
        assertEquals("https://live.radioantena.bg/RadioAntena", loaded.firstOrNull { it.id == "radio-antena" }?.url)
        assertEquals(defaultStations.size + 1, loaded.size)
        assertTrue(catalog.save(null, "Another", "https://example.org/live", "", "").isSuccess)
    }

    @Test
    fun rejectsInvalidFieldsAndUnknownIdWithoutChangingTheCatalog() = runBlocking {
        val file = storeFile()
        val catalog = StationCatalog(file)
        val initial = catalog.stations.value
        val validUrl = "https://example.org/live"

        assertTrue(catalog.save(null, " \t ", validUrl, "", "").isFailure)
        listOf("", "https:///live", "https://example.org:wrong/live",
            "https://example.org:65536/live", "ftp://example.org/live", "live.m3u8",
            "https://example.org/live song").forEach { invalid ->
            assertTrue(invalid, catalog.save(null, "Station", invalid, "", "").isFailure)
        }
        assertTrue(catalog.save(null, "Station", validUrl, "file:///a", "").isFailure)
        assertTrue(catalog.save(null, "Station", validUrl, "", "rtsp://example.org/a").isFailure)
        assertTrue(catalog.save("missing", "Station", validUrl, "", "").isFailure)
        assertEquals(initial, catalog.stations.value)
        assertTrue(!file.exists())
    }

    @Test
    fun malformedCatalogRemainsIntactWhenSaving() = runBlocking {
        val file = storeFile()
        val damaged = """[{"id":"zrock","name":123}]"""
        file.writeText(damaged)
        val catalog = StationCatalog(file)
        val initial = catalog.stations.value

        assertEquals(defaultStations, initial)
        assertTrue(catalog.save(null, "Station", "https://example.org/live", "", "").isFailure)
        assertEquals(initial, catalog.stations.value)
        assertEquals(damaged, file.readText())
    }

    @Test
    fun failedWriteDoesNotPublishTheStationAndCanBeRetried() = runBlocking {
        val parent = File(directory.root, "catalog-directory").apply { mkdir() }
        val file = File(parent, "stations.json")
        val catalog = StationCatalog(file)
        val initial = catalog.stations.value
        assertTrue(parent.delete())

        assertTrue(catalog.save(null, "Station", "https://example.org/live", "", "").isFailure)
        assertEquals(initial, catalog.stations.value)

        assertTrue(parent.mkdir())
        val saved = catalog.save(null, "Station", "https://example.org/live", "", "").getOrThrow()
        assertEquals(saved, catalog.stations.value.last())
    }

    @Test
    fun navigationWrapsByIdAndHandlesAnAbsentSelection() {
        val stations = StationCatalog(storeFile()).stations.value.take(3)
        assertEquals(stations.last(), adjacentStation(stations, stations.first().id, -1))
        assertEquals(stations.first(), adjacentStation(stations, stations.last().id, 1))
        assertEquals(stations.first(), adjacentStation(stations, null, -1))
        assertEquals(stations.first(), adjacentStation(stations, "unknown", 1))
        assertEquals(stations.last(), adjacentStation(stations, stations.last().id, Int.MIN_VALUE + 2))
        assertNull(adjacentStation(emptyList(), null, 1))
    }

    private fun storeFile() = File(directory.root, "stations.json")
}
