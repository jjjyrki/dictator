package io.jyri.dictator.speech

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import io.jyri.dictator.insert.TranscriptNoise
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Speech engine for the accessibility-service path. Each session owns its
 * microphone capture; audio accumulates and is transcribed once on finish
 * through the process-wide Whisper context. With partials enabled a second
 * thread periodically transcribes the buffer so the overlay can show draft
 * text while recording.
 */
class WhisperLiveSpeechEngine(
    private val sharedEngine: WhisperSttEngine,
    private val threads: Int = DEFAULT_THREADS,
    private val partialsEnabled: Boolean = false,
) : SpeechEngine {
    override fun start(): SpeechSession = LiveSession(sharedEngine, threads, partialsEnabled)

    companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val FRAME_SAMPLES = 1_600
        private const val DEFAULT_THREADS = 4
        const val PCM16_SCALE = 32_768f
    }
}

private class LiveSession(
    private val sharedEngine: WhisperSttEngine,
    private val threads: Int,
    partialsEnabled: Boolean,
) : SpeechSession {
    private val bufferLock = Any()
    private var buffer = FloatArray(INITIAL_CAPACITY)
    private var bufferedSamples = 0
    private var failure: Throwable? = null
    private var levelListener: ((Float) -> Unit)? = null
    private var partialListener: ((String) -> Unit)? = null
    private var lastPartial = ""
    private var lastPartialSamples = 0
    @Volatile
    private var latestRms = 0f
    private val capturing = AtomicBoolean(true)
    private val captureThread = Thread(::captureLoop, "dictator-live-capture")
    private val partialThread = if (partialsEnabled) Thread(::partialLoop, "dictator-live-partial") else null

    init {
        captureThread.start()
        partialThread?.start()
    }

    override fun feedAudio(pcm16kMono: FloatArray) {
        // Capture is engine-driven; external feeds are not used in live sessions.
    }

    override fun setLevelListener(listener: ((Float) -> Unit)?) {
        levelListener = listener
    }

    override fun setPartialListener(listener: ((String) -> Unit)?) {
        partialListener = listener
    }

    override fun finish(): String {
        capturing.set(false)
        captureThread.join()
        // Wait out any in-flight partial transcription; the shared native
        // context must not run two whisper_full calls concurrently.
        partialThread?.join()
        failure?.let { throw it }
        val clip = synchronized(bufferLock) {
            if (bufferedSamples == 0) return ""
            if (bufferedSamples == buffer.size) buffer else buffer.copyOf(bufferedSamples)
        }
        return sharedEngine.transcribe(clip)
    }

    override fun cancel() {
        capturing.set(false)
        synchronized(bufferLock) { bufferedSamples = 0 }
    }

    /** Periodically transcribes the buffered audio and emits draft text. */
    private fun partialLoop() {
        while (capturing.get()) {
            Thread.sleep(PARTIAL_INTERVAL_MS)
            if (!capturing.get()) break
            if (latestRms < PARTIAL_SILENCE_RMS) continue
            val clip = synchronized(bufferLock) {
                when {
                    bufferedSamples < MIN_PARTIAL_SAMPLES -> null
                    bufferedSamples == lastPartialSamples -> null
                    else -> {
                        lastPartialSamples = bufferedSamples
                        val start = (bufferedSamples - MAX_PARTIAL_SAMPLES).coerceAtLeast(0)
                        buffer.copyOfRange(start, bufferedSamples)
                    }
                }
            } ?: continue
            // A partial failure must not kill the session; the final
            // transcription at finish is the source of truth.
            val text = runCatching { sharedEngine.transcribe(clip) }.getOrNull() ?: continue
            val cleaned = TranscriptNoise.usableSpeech(text) ?: continue
            if (cleaned != lastPartial) {
                lastPartial = cleaned
                partialListener?.invoke(cleaned)
            }
        }
    }

    private fun captureLoop() {
        val recorder = openRecorder()
        if (recorder == null) {
            failure = failure ?: IllegalStateException("Could not open the microphone at 16 kHz")
            return
        }
        try {
            recorder.startRecording()
            val pcm16 = ShortArray(WhisperLiveSpeechEngine.FRAME_SAMPLES)
            while (capturing.get()) {
                val count = recorder.read(pcm16, 0, pcm16.size, AudioRecord.READ_BLOCKING)
                if (count <= 0) {
                    if (count == AudioRecord.ERROR_INVALID_OPERATION) {
                        failure = IllegalStateException("Microphone stopped unexpectedly")
                        return
                    }
                    continue
                }
                var sumSquares = 0.0
                val frame = FloatArray(count)
                for (index in 0 until count) {
                    val sample = pcm16[index] / WhisperLiveSpeechEngine.PCM16_SCALE
                    frame[index] = sample
                    sumSquares += sample.toDouble() * sample.toDouble()
                }
                val rms = sqrt(sumSquares / count)
                latestRms = rms.toFloat()
                levelListener?.invoke(rms.toFloat() * LEVEL_GAIN)
                synchronized(bufferLock) {
                    if (capturing.get()) {
                        if (bufferedSamples + count > buffer.size) {
                            buffer = buffer.copyOf(maxOf(buffer.size * 2, bufferedSamples + count))
                        }
                        frame.copyInto(buffer, bufferedSamples)
                        bufferedSamples += count
                    }
                }
            }
        } catch (error: Throwable) {
            failure = error
        } finally {
            runCatching {
                recorder.stop()
                recorder.release()
            }
        }
    }

    private fun openRecorder(): AudioRecord? {
        return try {
            val minBuffer = AudioRecord.getMinBufferSize(
                WhisperLiveSpeechEngine.SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(WhisperLiveSpeechEngine.SAMPLE_RATE_HZ)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minBuffer, WhisperLiveSpeechEngine.FRAME_SAMPLES * 8))
                .build()
                .takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        } catch (error: SecurityException) {
            failure = failure ?: error
            null
        } catch (error: Throwable) {
            failure = failure ?: error
            null
        }
    }

    private companion object {
        const val INITIAL_CAPACITY = 16_000 * 30
        // VOICE_RECOGNITION input is deliberately quiet on Samsung devices;
        // boost it enough for normal speech to visibly drive the waveform.
        const val LEVEL_GAIN = 18f
        const val PARTIAL_INTERVAL_MS = 1_500L
        // Whisper needs at least about a second of audio to say anything.
        const val MIN_PARTIAL_SAMPLES = 16_000
        // Keep opt-in partial inference bounded; final transcription still uses
        // the complete recording.
        const val MAX_PARTIAL_SAMPLES = 16_000 * 30
        // Match the quiet-room gate used by the waveform, before its visual gain.
        const val PARTIAL_SILENCE_RMS = 0.005f
    }
}
