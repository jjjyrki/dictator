package io.jyri.dictator

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import io.jyri.dictator.focus.TargetSnapshot
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal fun sanitizeDiagnosticEvent(event: String): String = event.replace('\n', ' ')

internal fun retainedDiagnosticBytes(
    existing: ByteArray,
    incoming: ByteArray,
    maxBytes: Int,
): ByteArray {
    val retainedStart = (existing.size + incoming.size - maxBytes).coerceAtLeast(0)
    val existingBytes = existing.size - retainedStart
    return ByteArray(existingBytes + incoming.size).also { result ->
        if (existingBytes > 0) {
            existing.copyInto(result, destinationOffset = 0, startIndex = retainedStart)
        }
        incoming.copyInto(result, destinationOffset = existingBytes)
    }
}

/**
 * Stores a small, privacy-safe diagnostic history for later export.
 *
 * Events contain metadata only: no transcript, audio, or field text.
 */
internal object DictationDiagnostics {
    private const val DIRECTORY = "diagnostics"
    private const val FILE_NAME = "dictation-events.log"
    private const val MAX_BYTES = 256 * 1024

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var file: File? = null

    fun initialize(context: Context) {
        file = File(context.applicationContext.filesDir, "$DIRECTORY/$FILE_NAME")
    }

    fun record(event: String) {
        val target = file ?: return
        val line = "${Instant.now()} ${sanitizeDiagnosticEvent(event)}\n"
        executor.execute {
            runCatching {
                target.parentFile?.mkdirs()
                val existing = if (target.exists()) target.readBytes() else ByteArray(0)
                val retained = retainedDiagnosticBytes(
                    existing = existing,
                    incoming = line.toByteArray(StandardCharsets.UTF_8),
                    maxBytes = MAX_BYTES,
                )
                target.writeBytes(retained)
            }
        }
    }

    fun export(context: Context, destination: Uri, onComplete: (Boolean) -> Unit) {
        val appContext = context.applicationContext
        executor.execute {
            val success = runCatching {
                appContext.contentResolver.openOutputStream(destination)?.use { output ->
                    val source = file
                    if (source?.isFile == true) {
                        source.inputStream().use { input -> input.copyTo(output) }
                    } else {
                        output.write("No diagnostic events recorded.\n".toByteArray(StandardCharsets.UTF_8))
                    }
                } ?: error("Unable to open export destination")
            }.isSuccess
            mainHandler.post { onComplete(success) }
        }
    }

    fun snapshot(snapshot: TargetSnapshot?): String = snapshot?.let {
        "package=${it.packageName} window=${it.windowId} class=${it.className} " +
            "viewId=${it.viewIdResourceName}"
    } ?: "null"
}
