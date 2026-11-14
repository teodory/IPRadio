package com.example.ipradio

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SongManagerTest {
    @Test(timeout = 4000)
    fun skipsLookupForStationLeftDuringShortDelay() = runBlocking {
        val lookups = Channel<String>(Channel.UNLIMITED)
        val manager = SongManager(this, enrichSong = { song ->
            lookups.trySend(song.title)
            song
        }) { _, _ -> }
        manager.setMusicBrainzEnabled(true)
        manager.setStation(station("a").copy(infoDataUrl = ""))
        manager.setStreamSong("a", SongInfo("First", "Artist"))
        yield()
        assertTrue("Lookup started before the station settled", lookups.tryReceive().isFailure)

        manager.setStation(station("b").copy(infoDataUrl = ""))
        manager.setStreamSong("b", SongInfo("Second", "Artist"))
        assertEquals("Second", withTimeout(2_500) { lookups.receive() })
        assertTrue(lookups.tryReceive().isFailure)
        manager.setStation(null)
    }

    @Test(timeout = 5000)
    fun enablingCoverLookupUpdatesCurrentSongAndDisablingRemovesTheCover() = runBlocking {
        val published = Channel<SongInfo?>(Channel.UNLIMITED)
        val manager = SongManager(this, enrichSong = {
            it.copy(artworkUrl = "https://example.org/catalog.jpg")
        }) { _, song ->
            published.trySend(song)
        }
        manager.setStation(station("a").copy(infoDataUrl = ""))
        val song = SongInfo("The Song", "The Artist")
        manager.setStreamSong("a", song)
        assertEquals(song, published.receive())
        assertTrue(published.tryReceive().isFailure)

        manager.setMusicBrainzEnabled(true)
        assertEquals(song.copy(artworkUrl = "https://example.org/catalog.jpg"), published.receive())
        manager.setMusicBrainzEnabled(false)
        assertEquals(song, published.receive())
        assertNull(manager.currentSong?.artworkUrl)
        manager.setStation(null)
    }

    @Test(timeout = 5000)
    fun stationArtworkStillAllowsMusicBrainzLookup() = runBlocking {
        val published = Channel<SongInfo?>(Channel.UNLIMITED)
        val manager = SongManager(this, enrichSong = { song ->
            song.copy(title = "The Song", artist = "The Artist")
        }) { _, song ->
            published.trySend(song)
        }
        manager.setMusicBrainzEnabled(true)
        manager.setStation(station("a").copy(infoDataUrl = ""))
        val song = SongInfo("THE SONG", "the artist", "https://example.org/station.jpg")
        manager.setStreamSong("a", song)
        assertEquals(song, published.receive())
        assertEquals(song.copy(title = "The Song", artist = "The Artist"), published.receive())
        manager.setMusicBrainzEnabled(false)
        assertEquals(song, published.receive())
        manager.setStation(null)
    }

    @Test(timeout = 5000)
    fun retriesFailedLookupWhileTheSameSongKeepsPlaying() = runBlocking {
        val published = Channel<SongInfo?>(Channel.UNLIMITED)
        var attempts = 0
        val manager = SongManager(this, enrichSong = { song ->
            if (++attempts == 1) throw IOException("Temporary MusicBrainz failure")
            song.copy(title = "The Song", artist = "The Artist")
        }) { _, song -> published.trySend(song) }
        manager.setMusicBrainzEnabled(true)
        manager.setStation(station("a").copy(infoDataUrl = ""))
        val original = SongInfo("THE SONG", "THE ARTIST")
        manager.setStreamSong("a", original)

        assertEquals(original, published.receive())
        assertEquals(original.copy(title = "The Song", artist = "The Artist"),
            withTimeout(3_500) { published.receive() })
        assertEquals(2, attempts)
        manager.setStation(null)
    }

    @Test(timeout = 5000)
    fun delayedCoverCannotBePublishedAfterChangingStations() = runBlocking {
        val published = mutableListOf<Pair<String, SongInfo?>>()
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val manager = SongManager(this, enrichSong = { song ->
            if (song.title == "First") withContext(Dispatchers.IO) {
                started.complete(Unit)
                check(release.await(3, TimeUnit.SECONDS))
                song.copy(artworkUrl = "https://example.org/old.jpg")
            } else song
        }) { id, song -> published.add(id to song) }
        val first = SongInfo("First", "Artist")
        val next = SongInfo("Next", "Artist", "https://example.org/next.jpg")
        manager.setMusicBrainzEnabled(true)
        manager.setStation(station("a").copy(infoDataUrl = ""))
        manager.setStreamSong("a", first)
        started.await()
        val oldLookup = coroutineContext[Job]!!.children.single()
        try {
            manager.setStation(station("b").copy(infoDataUrl = ""))
            manager.setStreamSong("b", next)
            assertTrue(oldLookup.isCancelled)
        } finally {
            release.countDown()
        }
        oldLookup.join()
        assertEquals(listOf("a" to first, "b" to next), published)
        manager.setStation(null)
    }

    @Test(timeout = 5000)
    fun dropsBlockedFetchWhenStationChanges() = verifyBlockedFetchIsDropped(station("b"))

    @Test(timeout = 5000)
    fun dropsBlockedFetchWhenTheSameStationsMetadataUrlChanges() =
        verifyBlockedFetchIsDropped(station("a").copy(infoDataUrl = "https://example.test/metadata/b"))

    @Test(timeout = 5000)
    fun dropsBlockedFetchWhenPlaybackStops() = verifyBlockedFetchIsDropped(null)

    @Test(timeout = 5000)
    fun preservesStreamSongWhenEditingOnlyTheStationMetadataUrl() = runBlocking {
        val manager = SongManager(this) { _, _ -> }
        val station = station("a")
        val song = SongInfo("The Song", "The Artist")
        manager.setStation(station.copy(infoDataUrl = ""), poll = false)
        manager.setStreamSong("a", song)
        manager.setStation(station, poll = false)

        assertEquals(song, manager.currentSong)
    }

    @Test(timeout = 5000)
    fun onlyUsesNewStreamSongsWhenTheStationEndpointStopsReporting() = runBlocking {
        val paused = Channel<Unit>()
        val advance = Channel<Unit>()
        val published = mutableListOf<SongInfo?>()
        val responses = listOf("Artist - First", null).iterator()
        val manager = SongManager(this, fetch = { responses.next() }, waitForNextPoll = {
            paused.send(Unit)
            advance.receive()
        }) { _, song -> published.add(song) }
        val older = SongInfo("Older", "Artist")
        val first = SongInfo("First", "Artist")
        val next = SongInfo("Next", "Artist")

        manager.setStation(station("a"))
        manager.setStreamSong("a", older)
        paused.receive()
        advance.send(Unit)
        paused.receive()
        assertEquals(listOf(older, first), published)

        manager.setStreamSong("a", next)
        manager.setStation(null)

        assertEquals(listOf(older, first, next), published)
    }

    @Test(timeout = 5000)
    fun retainsLastUsableTitleAndKeepsPollingAfterFailure() = runBlocking {
        val pauseReached = Channel<Unit>()
        val nextPoll = Channel<Unit>()
        val published = mutableListOf<Pair<String, String?>>()
        val responses = listOf<() -> String?>(
            { "Artist - First" },
            { throw IOException("temporary failure") },
            { "<html>Unavailable</html>" },
            { "Artist - First" },
            { "Artist - Next" },
        ).iterator()
        val manager = SongManager(this, fetch = { responses.next()() }, waitForNextPoll = {
            pauseReached.send(Unit)
            nextPoll.receive()
        }) { id, song -> published.add(id to song?.displayTitle) }

        manager.setStation(station("a"))
        pauseReached.receive()
        val originalPoll = coroutineContext[Job]!!.children.single()
        manager.setStation(station("a").copy(name = "Renamed"))
        assertSame(originalPoll, coroutineContext[Job]!!.children.single())

        repeat(3) {
            nextPoll.send(Unit)
            pauseReached.receive()
            assertEquals(listOf("a" to "Artist - First"), published)
        }
        nextPoll.send(Unit)
        pauseReached.receive()
        assertEquals(listOf("a" to "Artist - First", "a" to "Artist - Next"), published)
        manager.setStation(null)
    }

    private fun verifyBlockedFetchIsDropped(next: Station?) = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val published = mutableListOf<Pair<String, String?>>()
        val nextPublished = Channel<Unit>(Channel.UNLIMITED)
        val manager = SongManager(this, fetch = { url ->
            if (url.endsWith("/a")) {
                started.complete(Unit)
                check(release.await(3, TimeUnit.SECONDS))
                "Old Artist - Old Song"
            } else {
                "New Artist - New Song"
            }
        }) { id, song ->
            val title = song?.displayTitle
            published.add(id to title)
            if (title == "New Artist - New Song") nextPublished.trySend(Unit)
        }

        manager.setStation(station("a"))
        started.await()
        val oldPoll = coroutineContext[Job]!!.children.single()
        try {
            manager.setStation(next)
            assertTrue("Previous metadata poll is still active", oldPoll.isCancelled)
            if (next != null) nextPublished.receive()
        } finally {
            release.countDown()
        }
        oldPoll.join()
        val expected = if (next == null) emptyList() else listOf(next.id to "New Artist - New Song")
        assertEquals(expected, published)
        manager.setStation(null)
    }

    private fun station(id: String) = Station(
        id = id,
        name = "Display name $id",
        url = "https://example.test/stream/$id",
        image = "",
        logo = 0,
        infoDataUrl = "https://example.test/metadata/$id",
        metadataFormat = MetadataFormat.PLAIN_TEXT,
    )
}
