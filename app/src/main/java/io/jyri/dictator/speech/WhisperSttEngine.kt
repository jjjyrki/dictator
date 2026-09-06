package io.jyri.dictator.speech

import io.jyri.dictator.model.SpokenLanguage
import java.io.File

/**
 * Whisper dictation engine backed by the native whisper.cpp runtime. Audio
 * accumulates during the session and is transcribed once on finish, following
 * FUTO's ACFT recipe (shortened audio context, greedy sampling).
 */
class WhisperSttEngine(
    modelFile: File,
    private val languageConfig: WhisperLanguageConfig = WhisperLanguageConfig.fixed(SpokenLanguage.ENGLISH),
    private val threads: Int = DEFAULT_THREADS,
) {
    private var handle: Long = nativeCreate(modelFile.absolutePath, threads)

    private var buffer = FloatArray(INITIAL_CAPACITY)
    private var bufferedSamples = 0

    fun start() {
        bufferedSamples = 0
    }

    fun feed(pcm16kMono: FloatArray) {
        if (bufferedSamples + pcm16kMono.size > buffer.size) {
            var capacity = buffer.size
            while (bufferedSamples + pcm16kMono.size > capacity) {
                capacity *= 2
            }
            buffer = buffer.copyOf(capacity)
        }
        pcm16kMono.copyInto(buffer, bufferedSamples)
        bufferedSamples += pcm16kMono.size
    }

    fun finish(): String {
        if (bufferedSamples == 0) return ""
        val clip = if (bufferedSamples == buffer.size) buffer else buffer.copyOf(bufferedSamples)
        return transcribeClip(clip)
    }

    /** Transcribes an externally captured 16 kHz mono clip on the shared native context. */
    fun transcribe(clip: FloatArray): String {
        check(handle != 0L) { "The Whisper engine is closed" }
        return transcribeClip(clip)
    }

    private fun transcribeClip(clip: FloatArray): String = nativeTranscribe(
        handle,
        clip,
        threads,
        languageConfig.language,
        languageConfig.allowedLanguageCodes.joinToString(","),
    )

    fun close() {
        if (handle != 0L) {
            nativeClose(handle)
            handle = 0L
        }
    }

    companion object {
        const val SAMPLE_RATE_HZ = 16_000
        private const val DEFAULT_THREADS = 4
        private const val INITIAL_CAPACITY = 16_000 * 30

        init {
            System.loadLibrary("dictator_whisper")
        }
    }

    private external fun nativeCreate(modelPath: String, threads: Int): Long

    private external fun nativeTranscribe(
        handle: Long,
        pcm16kMono: FloatArray,
        threads: Int,
        language: String,
        allowedLanguages: String,
    ): String

    private external fun nativeClose(handle: Long)
}
