package com.example.ipradio

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyPlaybackTest {
    @Test
    fun nextAndPlayFromSearchWorkBeforeBrowsing() {
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

        ActivityScenario.launch(MainActivity::class.java).use {
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
                instrumentation.runOnMainSync {
                    connected.registerCallback(listener)
                    connected.transportControls.skipToNext()
                }
                assertTrue("Next should select the first station", awaitStation(stationIds, "zrock"))
                instrumentation.runOnMainSync {
                    connected.transportControls.playFromSearch("  Magic FM  ", null)
                }
                assertTrue("Voice search should select Magic FM", awaitStation(stationIds, "magic-fm"))
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
