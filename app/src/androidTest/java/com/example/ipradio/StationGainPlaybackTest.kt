package com.example.ipradio

import android.content.ComponentName
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StationGainPlaybackTest {
    @Test
    fun switchingStationsAppliesEachSavedVolume() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val catalog = StationCatalog.getInstance(context)
        val first = catalog.allStations.value.first { it.id == "zrock" }
        val second = catalog.allStations.value.first { it.id == "1rock" }
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        var future: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
        var controller: MediaController? = null

        try {
            runBlocking {
                catalog.save(first.id, first.name, first.url, first.image, first.infoDataUrl,
                    true, -6).getOrThrow()
                catalog.save(second.id, second.name, second.url, second.image,
                    second.infoDataUrl, true, 0).getOrThrow()
            }
            instrumentation.runOnMainSync {
                future = MediaController.Builder(context, token).buildAsync()
            }
            controller = checkNotNull(future).get(10, TimeUnit.SECONDS)

            fun select(id: String) = instrumentation.runOnMainSync {
                controller.setMediaItem(MediaItem.Builder().setMediaId(id).build())
                controller.prepare()
            }
            fun awaitVolume(id: String, expected: Float): Boolean {
                repeat(100) {
                    var matched = false
                    instrumentation.runOnMainSync {
                        matched = controller.currentMediaItem?.mediaId == id &&
                            kotlin.math.abs(controller.volume - expected) < 0.01f
                    }
                    if (matched) return true
                    Thread.sleep(50)
                }
                return false
            }

            select(first.id)
            assertTrue("-6 dB should attenuate the first station", awaitVolume(first.id, 0.501f))
            runBlocking {
                catalog.save(first.id, first.name, first.url, first.image, first.infoDataUrl,
                    true, -3).getOrThrow()
            }
            assertTrue("Editing the playing station should update its volume", awaitVolume(first.id, 0.708f))
            select(second.id)
            assertTrue("The next station should return to its own volume", awaitVolume(second.id, 1f))
        } finally {
            instrumentation.runOnMainSync {
                controller?.stop()
                future?.let(MediaController::releaseFuture)
            }
            runBlocking {
                catalog.save(first.id, first.name, first.url, first.image, first.infoDataUrl,
                    first.enabled, first.gainDb).getOrThrow()
                catalog.save(second.id, second.name, second.url, second.image,
                    second.infoDataUrl, second.enabled, second.gainDb).getOrThrow()
            }
        }
    }
}
