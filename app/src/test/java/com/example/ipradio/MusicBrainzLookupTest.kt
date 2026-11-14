package com.example.ipradio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class MusicBrainzLookupTest {
    @Test
    fun findsAnAvailableCoverForTheMatchingRecordingAndCachesIt() = runBlocking {
        val missing = "https://coverartarchive.org/release/11111111-1111-1111-1111-111111111111/front-250"
        val available = "https://coverartarchive.org/release/22222222-2222-2222-2222-222222222222/front-250"
        val checked = mutableListOf<String>()
        val lookup = MusicBrainzLookup(
            search = {
                """{"count":2,"recordings":[
                    {"title":"One Song","artist-credit":[{"name":"Other Artist"}],
                     "releases":[{"id":"33333333-3333-3333-3333-333333333333"}]},
                    {"title":"One Song","artist-credit":[{"name":"The Artist"}],
                     "releases":[{"id":"11111111-1111-1111-1111-111111111111"},
                                 {"id":"22222222-2222-2222-2222-222222222222"}]}
                ]}"""
            },
            hasCover = { url ->
                checked.add(url)
                url == available
            }
        )

        assertEquals(available, lookup.enrich(SongInfo("ONE SONG", "The Artist")).artworkUrl)
        assertEquals(available, lookup.enrich(SongInfo("One Song", "the artist")).artworkUrl)
        assertEquals(listOf(missing, available), checked)
    }

    @Test
    fun doesNotSearchWhenArtistIsMissing() = runBlocking {
        val lookup = MusicBrainzLookup(search = { error("Song has no artist") })

        assertEquals(SongInfo("One Song"), lookup.enrich(SongInfo("One Song")))
    }

    @Test
    fun usesCatalogSpellingWithoutCheckingForAnotherCover() = runBlocking {
        val stationCover = "https://example.org/station.jpg"
        val song = SongInfo("CAN'T STOP LOVIN' YOU", "van halen", stationCover)
        val lookup = MusicBrainzLookup(
            search = { url ->
                val query = URLDecoder.decode(url, "UTF-8")
                assertTrue(query.contains("recording:\"CAN'T STOP LOVIN' YOU\""))
                """{"count":3,"recordings":[
                    {"title":"Can’t Stop Lovin’ You","artist-credit":[{"name":"Van Halen"}],
                     "releases":[{"id":"11111111-1111-1111-1111-111111111111"}]},
                    {"title":"Can't Stop Lovin' You","artist-credit":[{"name":"Van Halen"}]},
                    {"title":"Can't Stop Lovin' You","artist-credit":[{"name":"Other Artist"}]}
                ]}"""
            },
            hasCover = { error("The radio already supplied a cover") }
        )

        assertEquals(song.copy(title = "Can’t Stop Lovin’ You", artist = "Van Halen"), lookup.enrich(song))
    }

    @Test
    fun keepsStationSpellingWhenMatchingRecordingsDisagree() = runBlocking {
        val song = SongInfo("ONE SONG", "THE ARTIST")
        val lookup = MusicBrainzLookup(search = {
            """{"count":2,"recordings":[
                {"title":"One Song","artist-credit":[{"name":"The Artist"}]},
                {"title":"One Song","artist-credit":[{"name":"the artist"}]}
            ]}"""
        })

        assertEquals(song, lookup.enrich(song))
    }

    @Test
    fun keepsStationSpellingWhenSomeSearchResultsAreMissing() = runBlocking {
        val song = SongInfo("BACK IN BLACK", "AC/DC", "https://example.org/station.jpg")
        val lookup = MusicBrainzLookup(search = {
            """{"count":182,"recordings":[{"title":"Back in Black","artist-credit":[{"name":"AC/DC"}]}]}"""
        })

        assertEquals(song, lookup.enrich(song))
    }

    @Test
    fun keepsStationTextWhenOnlyTheTitleMatches() = runBlocking {
        val song = SongInfo("ONE SONG", "THE ARTIST")
        val lookup = MusicBrainzLookup(search = {
            """{"count":1,"recordings":[{"title":"One Song","artist-credit":[{"name":"Other Artist"}]}]}"""
        })

        assertEquals(song, lookup.enrich(song))
    }

    @Test
    fun reusesTheSameRecordingSearchForEmbeddedArtAndMissingArt() = runBlocking {
        val coverUrl = "https://coverartarchive.org/release/11111111-1111-1111-1111-111111111111/front-250"
        val song = SongInfo("BACK IN BLACK", "AC/DC", artworkData = byteArrayOf(1))
        var searches = 0
        val coversChecked = mutableListOf<String>()
        val lookup = MusicBrainzLookup(
            search = {
                searches++
                """{"count":1,"recordings":[{"title":"Back in Black","artist-credit":[{"name":"AC/DC"}],
                    "releases":[{"id":"11111111-1111-1111-1111-111111111111"}]}]}"""
            },
            hasCover = {
                coversChecked.add(it)
                it == coverUrl
            }
        )

        assertEquals(song.copy(title = "Back in Black"), lookup.enrich(song))
        assertTrue(coversChecked.isEmpty())
        assertEquals(SongInfo("Back in Black", "AC/DC", coverUrl), lookup.enrich(song.copy(artworkData = null)))
        assertEquals(1, searches)
        assertEquals(listOf(coverUrl), coversChecked)
    }

    @Test
    fun prefersTheStudioAlbumCoverOverBootlegsAndLiveAlbums() = runBlocking {
        val lookup = MusicBrainzLookup(
            search = {
                """{"count":1,"recordings":[{"title":"One Song","artist-credit":[{"name":"The Artist"}],
                    "releases":[{"id":"11111111-1111-1111-1111-111111111111","status":"Bootleg"},
                        {"id":"22222222-2222-2222-2222-222222222222","release-group":{"primary-type":"Album","secondary-types":["Live"]}},
                        {"id":"33333333-3333-3333-3333-333333333333","release-group":{"primary-type":"Album","secondary-types":[]}}]
                }]}"""
            },
            hasCover = { true }
        )

        assertEquals("https://coverartarchive.org/release/33333333-3333-3333-3333-333333333333/front-250",
            lookup.enrich(SongInfo("One Song", "The Artist")).artworkUrl)
    }
}
