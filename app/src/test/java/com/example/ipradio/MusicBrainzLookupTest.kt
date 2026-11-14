package com.example.ipradio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicInteger

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
    fun usesDominantSpellingWhenOneRecordingHasDifferentCapitalization() = runBlocking {
        val lookup = MusicBrainzLookup(search = {
            """{"count":75,"recordings":[
                {"title":"Lay Your Hands on Me","artist-credit":[{"name":"Bon Jovi"}]},
                {"title":"Lay Your Hands On Me","artist-credit":[{"name":"Bon Jovi"}]},
                {"title":"Lay Your Hands on Me","artist-credit":[{"name":"Bon Jovi"}]},
                {"title":"Lay Your Hands on Me","artist-credit":[{"name":"Bon Jovi"}]}
            ]}"""
        })

        assertEquals(SongInfo("Lay Your Hands on Me", "Bon Jovi"),
            lookup.enrich(SongInfo("LAY YOUR HANDS ON ME", "BON JOVI")))
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
    fun usesConsistentSpellingFromSeveralExactMatchesEvenWhenMoreResultsExist() = runBlocking {
        val lookup = MusicBrainzLookup(search = {
            """{"count":14,"recordings":[
                {"title":"Love Again","artist-credit":[{"name":"Dua Lipa"}]},
                {"title":"Love Again","artist-credit":[{"name":"Dua Lipa"}]},
                {"title":"Love Again (Remix)","artist-credit":[{"name":"Dua Lipa"}]}
            ]}"""
        })

        assertEquals(SongInfo("Love Again", "Dua Lipa"), lookup.enrich(SongInfo("LOVE AGAIN", "DUA LIPA")))
    }

    @Test
    fun searchesAndMatchesArtistWithSpacesAroundSlash() = runBlocking {
        val lookup = MusicBrainzLookup(search = { url ->
            assertTrue(URLDecoder.decode(url, "UTF-8").contains("artist:\"AC/DC\""))
            """{"count":2,"recordings":[
                {"title":"Thunderstruck","artist-credit":[{"name":"AC/DC"}]},
                {"title":"Thunderstruck","artist-credit":[{"name":"AC/DC"}]}
            ]}"""
        })

        assertEquals(SongInfo("Thunderstruck", "AC/DC"), lookup.enrich(SongInfo("THUNDERSTRUCK", "AC / DC")))
    }

    @Test
    fun matchesSpanishAndInRadioArtistCredit() = runBlocking {
        val cover = "https://coverartarchive.org/release/11111111-1111-1111-1111-111111111111/front-250"
        val lookup = MusicBrainzLookup(search = { url ->
            assertTrue(URLDecoder.decode(url, "UTF-8").contains("artist:\"Puff Daddy & Faith Evans\""))
            """{"count":2,"recordings":[
                {"title":"I'll Be Missing You","artist-credit":[{"name":"Puff Daddy","joinphrase":" & "},{"name":"Faith Evans"}],
                 "releases":[{"id":"11111111-1111-1111-1111-111111111111"}]},
                {"title":"I’ll Be Missing You","artist-credit":[{"name":"Puff Daddy","joinphrase":" & "},{"name":"Faith Evans"}]}
            ]}"""
        }, hasCover = { true })

        assertEquals(SongInfo("I'll Be Missing You", "Puff Daddy & Faith Evans", cover),
            lookup.enrich(SongInfo("I'll be missing you", "Puff Daddy y Faith Evans")))
    }

    @Test
    fun retriesTemporaryMusicBrainzFailure() = runBlocking {
        val requests = AtomicInteger()
        val server = ServerSocket(0, 2, InetAddress.getLoopbackAddress())
        val worker = Thread {
            repeat(2) {
                server.accept().use { socket ->
                    val request = BufferedReader(InputStreamReader(socket.getInputStream()))
                    while (!request.readLine().isNullOrEmpty()) Unit
                    val status = if (requests.incrementAndGet() == 1) 503 else 200
                    val body = if (status == 503) "busy" else """{"count":1,"recordings":[
                        {"title":"Love Again","artist-credit":[{"name":"Dua Lipa"}]}
                    ]}"""
                    val bytes = body.toByteArray()
                    socket.getOutputStream().write(
                        "HTTP/1.1 $status Test\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray() + bytes
                    )
                }
            }
        }
        worker.start()
        try {
            val url = "http://127.0.0.1:${server.localPort}/recording"
            val lookup = MusicBrainzLookup(search = { fetchMetadata(url) })

            assertEquals(SongInfo("Love Again", "Dua Lipa"), lookup.enrich(SongInfo("LOVE AGAIN", "DUA LIPA")))
            assertEquals(2, requests.get())
        } finally {
            server.close()
            worker.join(2_000)
        }
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
    fun matchesRecordingsCreditedToAnArtistAlias() = runBlocking {
        val cover = "https://coverartarchive.org/release/11111111-1111-1111-1111-111111111111/front-250"
        val lookup = MusicBrainzLookup(search = { url ->
            val query = URLDecoder.decode(url, "UTF-8")
            when {
                url.contains("/artist/") -> """{"count":1,"artists":[{
                    "id":"22222222-2222-2222-2222-222222222222","name":"MILEY",
                    "aliases":[{"name":"MILEY"},{"name":"Miley Cyrus"}]
                }]}"""
                query.contains("arid:22222222-2222-2222-2222-222222222222") ->
                    """{"count":1,"recordings":[{
                        "title":"Let’s Get Married","artist-credit":[{"name":"MILEY",
                        "artist":{"id":"22222222-2222-2222-2222-222222222222","name":"MILEY"}}],
                        "releases":[{"id":"11111111-1111-1111-1111-111111111111"}]
                    }]}"""
                else -> """{"count":0,"recordings":[]}"""
            }
        }, hasCover = { true })

        assertEquals(SongInfo("Let’s Get Married", "Miley Cyrus", cover),
            lookup.enrich(SongInfo("LET'S GET MARRIED", "MILEY CYRUS")))
    }

    @Test
    fun matchesFeaturedArtistWhenRadioPutsCreditInTitle() = runBlocking {
        val cover = "https://coverartarchive.org/release/11111111-1111-1111-1111-111111111111/front-250"
        val lookup = MusicBrainzLookup(search = { url ->
            val query = URLDecoder.decode(url, "UTF-8")
            if (query.contains("recording:\"AIN'T NOBODY\"")) {
                """{"count":3,"recordings":[
                    {"title":"Ain't Nobody","artist-credit":[{"name":"Felix Jaehn"}]},
                    {"title":"Ain't Nobody (Loves Me Better)","artist-credit":[
                        {"name":"Felix Jaehn","joinphrase":" feat. "},{"name":"Jasmine Thompson"}],
                        "releases":[{"id":"11111111-1111-1111-1111-111111111111"}]},
                    {"title":"Ain’t Nobody (Loves Me Better)","artist-credit":[
                        {"name":"Felix Jaehn","joinphrase":" feat. "},{"name":"Jasmine Thompson"}]}
                ]}"""
            } else """{"count":0,"recordings":[]}"""
        }, hasCover = { true })

        assertEquals(SongInfo("Ain't Nobody (Loves Me Better)", "Felix Jaehn feat. Jasmine Thompson", cover),
            lookup.enrich(SongInfo("AIN'T NOBODY (LOVE ME BETTER) (FEAT JASMINE THOMPSON)", "FELIX JAEHN")))
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
