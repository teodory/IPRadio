package com.example.ipradio

import android.content.ComponentName
import android.media.browse.MediaBrowser
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyBrowseTest {
    @Test
    fun legacyBrowserReceivesStableStationIdsAndLocalArtwork() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val result = LinkedBlockingQueue<Result<List<MediaBrowser.MediaItem>>>()
        lateinit var browser: MediaBrowser

        instrumentation.runOnMainSync {
            browser = MediaBrowser(
                context,
                ComponentName(context, PlaybackService::class.java),
                object : MediaBrowser.ConnectionCallback() {
                    override fun onConnected() {
                        browser.subscribe(browser.root, object : MediaBrowser.SubscriptionCallback() {
                            override fun onChildrenLoaded(
                                parentId: String,
                                children: MutableList<MediaBrowser.MediaItem>
                            ) {
                                result.add(Result.success(children.toList()))
                            }

                            override fun onError(parentId: String) {
                                result.add(Result.failure(AssertionError("Unable to load stations")))
                            }
                        })
                    }

                    override fun onConnectionFailed() {
                        result.add(Result.failure(AssertionError("Legacy browser connection rejected")))
                    }
                },
                null
            )
            browser.connect()
        }

        try {
            val stations = checkNotNull(result.poll(10, TimeUnit.SECONDS)) {
                "Legacy browser timed out"
            }.getOrThrow()
            assertTrue(stations.any { it.mediaId == "zrock" && it.isPlayable })
            assertTrue(stations.map { it.mediaId }.distinct().size == stations.size)
            assertTrue(stations.all {
                it.description.iconUri?.scheme in listOf("android.resource", "content") &&
                    it.description.mediaUri == null
            })
        } finally {
            instrumentation.runOnMainSync { browser.disconnect() }
        }
    }
}
