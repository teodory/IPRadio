package com.example.ipradio

import android.app.SearchManager
import android.content.ComponentName
import android.content.Intent
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyPlaybackTest {
    @Test
    fun voiceDiagnosticsRetainsRecentEventsWithinSizeLimit() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val trace = File(context.getExternalFilesDir(null), "voice-diagnostics.log")
        trace.delete()

        repeat(250) { index ->
            VoiceDiagnostics.record(context, "entry=$index ${"x".repeat(280)}")
        }

        assertTrue("Voice diagnostics must stay below 64 KiB", trace.length() <= 64 * 1024)
        val contents = trace.readText()
        assertTrue("The newest event must be retained", contents.contains("entry=249 "))
        assertFalse("Old events must be discarded", contents.contains("entry=0 "))
    }

    @Test
    fun voiceSearchWritesPersistentDiagnostics() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val trace = File(context.getExternalFilesDir(null), "voice-diagnostics.log")
        trace.delete()

        withLegacyController { controller, stationIds ->
            instrumentation.runOnMainSync {
                controller.transportControls.playFromSearch("Magic FM", null)
            }
            assertTrue("Voice search should select Magic FM", awaitStation(stationIds, "magic-fm"))
        }

        assertTrue("Voice diagnostics should survive the Activity", trace.isFile)
        assertTrue(trace.readText().contains("search=Magic FM"))
    }

    @Test
    fun mediaSearchIntentPlaysNamedStation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val voiceIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .addCategory(Intent.CATEGORY_DEFAULT)
            .setPackage(context.packageName)
            .putExtra(SearchManager.QUERY, "Magic FM")
        assertTrue(
            "The voice search intent must resolve to IPRadio",
            context.packageManager.queryIntentActivities(voiceIntent, 0).isNotEmpty()
        )

        withLegacyController(voiceIntent) { controller, stationIds ->
            val currentId = controller.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
            assertTrue("Voice search should play Magic FM",
                currentId == "magic-fm" || awaitStation(stationIds, "magic-fm"))
        }
    }

    @Test
    fun nextAndPlayFromSearchWorkBeforeBrowsing() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        withLegacyController { controller, stationIds ->
            instrumentation.runOnMainSync { controller.transportControls.skipToNext() }
            assertTrue("Next should select the first station", awaitStation(stationIds, "zrock"))
            instrumentation.runOnMainSync {
                controller.transportControls.playFromSearch("  Magic FM  ", null)
            }
            assertTrue("Voice search should select Magic FM", awaitStation(stationIds, "magic-fm"))
        }
    }

    @Test
    fun playResumesLastStationAfterServiceRestart() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val previousSession = withLegacyController { controller, stationIds ->
            instrumentation.runOnMainSync {
                controller.transportControls.playFromMediaId("1rock", null)
            }
            assertTrue("Should select 1Rock", awaitStation(stationIds, "1rock"))
            controller.sessionToken
        }
        context.stopService(Intent(context, PlaybackService::class.java))

        withLegacyController { controller, stationIds ->
            assertNotEquals("The playback service should have a new session", previousSession, controller.sessionToken)
            val state = controller.playbackState?.state
            assertFalse("Connecting should be silent", state == PlaybackState.STATE_PLAYING ||
                state == PlaybackState.STATE_BUFFERING)
            instrumentation.runOnMainSync { controller.transportControls.play() }
            assertEquals("1rock", stationIds.poll(10, TimeUnit.SECONDS))
        }
    }

    private fun <T> withLegacyController(
        launchIntent: Intent? = null,
        test: (MediaController, LinkedBlockingQueue<String>) -> T
    ): T {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val stationIds = LinkedBlockingQueue<String>()
        val connection = LinkedBlockingQueue<Result<MediaController>>()
        lateinit var browser: MediaBrowser
        val listener = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)?.let(stationIds::add)
            }
        }

        return ActivityScenario.launch<MainActivity>(launchIntent ?: Intent(context, MainActivity::class.java)).use {
            instrumentation.runOnMainSync {
                browser = MediaBrowser(
                    context,
                    ComponentName(context, PlaybackService::class.java),
                    object : MediaBrowser.ConnectionCallback() {
                        override fun onConnected() {
                            connection.add(Result.success(MediaController(context, browser.sessionToken)))
                        }

                        override fun onConnectionFailed() {
                            connection.add(Result.failure(AssertionError("Legacy browser connection rejected")))
                        }
                    },
                    null
                )
                browser.connect()
            }

            var controller: MediaController? = null
            try {
                val connected = checkNotNull(connection.poll(10, TimeUnit.SECONDS)) {
                    "Legacy browser timed out"
                }.getOrThrow()
                controller = connected
                instrumentation.runOnMainSync { connected.registerCallback(listener) }
                test(connected, stationIds)
            } finally {
                instrumentation.runOnMainSync {
                    controller?.unregisterCallback(listener)
                    controller?.transportControls?.stop()
                    browser.disconnect()
                }
            }
        }
    }

    private fun awaitStation(stationIds: LinkedBlockingQueue<String>, id: String): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (true) {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) return false
            val selected = stationIds.poll(remaining, TimeUnit.NANOSECONDS) ?: return false
            if (selected == id) return true
        }
    }
}
