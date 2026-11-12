package com.example.ipradio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicBrainzArtworkTest {
    @Test
    fun findsAnAvailableCoverForTheMatchingRecordingAndCachesIt() = runBlocking {
        val missing = "https://coverartarchive.org/release/11111111-1111-1111-1111-111111111111/front-250"
        val available = "https://coverartarchive.org/release/22222222-2222-2222-2222-222222222222/front-250"
        val checked = mutableListOf<String>()
        val lookup = MusicBrainzArtwork(
            search = {
                """{"recordings":[
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

        assertEquals(available, lookup.find(SongInfo("ONE SONG", "The Artist")))
        assertEquals(available, lookup.find(SongInfo("One Song", "the artist")))
        assertEquals(listOf(missing, available), checked)
    }

    @Test
    fun doesNotSearchWhenArtistIsMissing() = runBlocking {
        val lookup = MusicBrainzArtwork(search = { error("Song has no artist") })

        assertNull(lookup.find(SongInfo("One Song")))
    }

    @Test
    fun prefersTheStudioAlbumCoverOverBootlegsAndLiveAlbums() = runBlocking {
        val lookup = MusicBrainzArtwork(
            search = {
                """{"recordings":[{"title":"One Song","artist-credit":[{"name":"The Artist"}],
                    "releases":[{"id":"11111111-1111-1111-1111-111111111111","status":"Bootleg"},
                        {"id":"22222222-2222-2222-2222-222222222222","release-group":{"primary-type":"Album","secondary-types":["Live"]}},
                        {"id":"33333333-3333-3333-3333-333333333333","release-group":{"primary-type":"Album","secondary-types":[]}}]
                }]}"""
            },
            hasCover = { true }
        )

        assertEquals("https://coverartarchive.org/release/33333333-3333-3333-3333-333333333333/front-250",
            lookup.find(SongInfo("One Song", "The Artist")))
    }
}
