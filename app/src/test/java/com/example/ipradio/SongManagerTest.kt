package com.example.ipradio

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SongManagerTest {
    @Test(timeout = 5000)
    fun dropsBlockedFetchWhenStationChanges() = verifyBlockedFetchIsDropped(station("b"))

    @Test(timeout = 5000)
    fun dropsBlockedFetchWhenTheSameStationsMetadataUrlChanges() =
        verifyBlockedFetchIsDropped(station("a").copy(infoDataUrl = "https://example.test/metadata/b"))

    @Test(timeout = 5000)
    fun dropsBlockedFetchWhenPlaybackStops() = verifyBlockedFetchIsDropped(null)

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
        }) { id, title -> published.add(id to title) }

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
        }) { id, title ->
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
