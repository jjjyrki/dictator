package io.jyri.dictator.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import io.jyri.dictator.speech.WhisperSttEngine
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class LiveSttRecorder(
    private val engine: WhisperSttEngine,
) {
    private val recording = AtomicBoolean(false)
    private val captureFinished = AtomicBoolean(false)
    private val frames = ArrayBlockingQueue<FloatArray>(MAX_QUEUED_FRAMES)
    private val capturedSamples = AtomicLong(0)
    private val droppedSamples = AtomicLong(0)
    private val inferenceNanos = AtomicLong(0)
    private val failure = AtomicReference<Throwable?>(null)

    private lateinit var captureThread: Thread
    private lateinit var inferenceThread: Thread

    fun start() {
        measureInference { engine.start() }
        recording.set(true)
        captureThread = Thread(::capture, "dictator-audio-capture").also { it.start() }
        inferenceThread = Thread(::infer, "dictator-stt-inference").also { it.start() }
    }

    fun stopAndFinish(): Result {
        recording.set(false)
        captureThread.join()
        inferenceThread.join()
        failure.get()?.let { throw it }
        val transcript = measureInference { engine.finish() }
        return Result(
            transcript = transcript,
            audioSeconds = capturedSamples.get().toDouble() / SAMPLE_RATE_HZ,
            inferenceSeconds = inferenceNanos.get().toDouble() / NANOS_PER_SECOND,
            droppedFrames = droppedSamples.get() / FRAME_SAMPLES,
        )
    }

    private fun capture() {
        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ),
            FRAME_SAMPLES * BYTES_PER_PCM16 * MAX_QUEUED_FRAMES,
        )
        val recorder = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE_HZ)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                )
                .setBufferSizeInBytes(bufferSize)
                .build()
        } catch (error: SecurityException) {
            failure.compareAndSet(null, error)
            recording.set(false)
            captureFinished.set(true)
            return
        }
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Could not open microphone at $SAMPLE_RATE_HZ Hz" }
            recorder.startRecording()
            val pcm16 = ShortArray(FRAME_SAMPLES)
            while (recording.get()) {
                val count = recorder.read(pcm16, 0, pcm16.size, AudioRecord.READ_BLOCKING)
                if (count <= 0) {
                    check(count != AudioRecord.ERROR_INVALID_OPERATION) { "Microphone stopped unexpectedly" }
                    continue
                }
                val pcm32 = FloatArray(count) { index -> pcm16[index] / PCM16_SCALE }
                capturedSamples.addAndGet(count.toLong())
                if (!frames.offer(pcm32)) droppedSamples.addAndGet(count.toLong())
            }
        } catch (error: Throwable) {
            failure.compareAndSet(null, error)
            recording.set(false)
        } finally {
            recorder.stop()
            recorder.release()
            captureFinished.set(true)
        }
    }

    private fun infer() {
        try {
            while (!captureFinished.get() || frames.isNotEmpty()) {
                val frame = frames.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS) ?: continue
                // Whisper transcribes once at finish; the test bench has no partials.
                measureInference { engine.feed(frame) }
            }
        } catch (error: Throwable) {
            failure.compareAndSet(null, error)
            recording.set(false)
        }
    }

    private fun <T> measureInference(block: () -> T): T {
        val started = System.nanoTime()
        return try {
            block()
        } finally {
            inferenceNanos.addAndGet(System.nanoTime() - started)
        }
    }

    data class Result(
        val transcript: String,
        val audioSeconds: Double,
        val inferenceSeconds: Double,
        val droppedFrames: Long,
    ) {
        val realTimeFactor: Double
            get() = if (audioSeconds == 0.0) Double.NaN else inferenceSeconds / audioSeconds
    }

    private companion object {
        const val SAMPLE_RATE_HZ = WhisperSttEngine.SAMPLE_RATE_HZ
        const val FRAME_SAMPLES = 1_600
        const val BYTES_PER_PCM16 = 2
        const val MAX_QUEUED_FRAMES = 8
        const val POLL_TIMEOUT_MS = 100L
        const val PCM16_SCALE = 32_768f
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
