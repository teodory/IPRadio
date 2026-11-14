package com.example.ipradio

import android.content.Context
import java.io.File
import java.io.IOException
import java.time.Instant

/** A small, persistent trace that can be retrieved over ADB after an Android Auto voice test. */
internal object VoiceDiagnostics {
    private const val FILE_NAME = "voice-diagnostics.log"
    private const val MAX_BYTES = 64 * 1024
    private const val RETAIN_BYTES = 32 * 1024

    @Synchronized
    fun record(context: Context, event: String) {
        try {
            val directory = context.getExternalFilesDir(null) ?: return
            val file = File(directory, FILE_NAME)
            val safeEvent = event.replace(Regex("[\\r\\n\\t]+"), " ").take(300)
            file.appendText("${Instant.now()} $safeEvent\n")
            if (file.length() > MAX_BYTES) {
                val bytes = file.readBytes()
                val tail = bytes.copyOfRange(bytes.size - RETAIN_BYTES, bytes.size)
                val firstLineEnd = tail.indexOf('\n'.code.toByte())
                file.writeBytes(tail.copyOfRange(firstLineEnd + 1, tail.size))
            }
        } catch (_: IOException) {
            // Diagnostics must never interrupt playback.
        } catch (_: SecurityException) {
            // External app storage may be temporarily unavailable.
        }
    }
}
