package io.jyri.dictator.session

import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import io.jyri.dictator.DictationDiagnostics
import io.jyri.dictator.focus.EditableTarget
import io.jyri.dictator.focus.TargetSnapshot
import io.jyri.dictator.insert.InsertionOutcome
import io.jyri.dictator.insert.TextInserter
import io.jyri.dictator.insert.TranscriptNoise
import io.jyri.dictator.speech.SpeechEngine
import io.jyri.dictator.speech.SpeechSession
import java.util.concurrent.Executor

class DictationSessionController(
    private val speechSessionFactory: () -> SpeechEngine?,
    private val inserter: TextInserter,
    private val resolveFreshNode: (TargetSnapshot?) -> AccessibilityNodeInfo?,
    private val onState: (DictationState) -> Unit,
    private val onLevel: (Float) -> Unit = {},
    private val onPartial: (String) -> Unit = {},
    private val onError: (String) -> Unit = {},
    private val microphonePermissionGranted: () -> Boolean = { true },
    private val onMicrophonePermissionRequired: () -> Unit = {},
    private val replaceAll: () -> Boolean = { false },
    private val partialsEnabled: () -> Boolean = { false },
    private val background: Executor = Executor { it.run() },
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) {
    var state: DictationState = DictationState.Idle
        private set

    private var captured: TargetSnapshot? = null
    private var session: SpeechSession? = null
    private var finishingSession: SpeechSession? = null
    private var operationGeneration = 0L
    private var pendingReset: Runnable? = null

    fun onTap() {
        when (state) {
            DictationState.Idle -> start()
            DictationState.MicrophonePermissionRequired -> onMicrophonePermissionRequired()
            DictationState.Recording -> stop()
            DictationState.Processing -> Unit
            DictationState.Done, DictationState.Error -> reset()
        }
    }

    fun reset() {
        operationGeneration++
        cancelPendingReset()
        session?.cancel()
        finishingSession?.cancel()
        session = null
        finishingSession = null
        captured = null
        publish(DictationState.Idle)
    }

    private fun start() {
        operationGeneration++
        cancelPendingReset()
        if (!microphonePermissionGranted()) {
            publish(DictationState.MicrophonePermissionRequired)
            onMicrophonePermissionRequired()
            return
        }
        val node = resolveFreshNode(null)
        if (node == null || !EditableTarget.isUsable(node)) {
            onError("No editable text field is focused")
            return publish(DictationState.Error)
        }
        val engine = speechSessionFactory()
        if (engine == null) {
            onError("Model is still loading — tap again in a moment")
            return publish(DictationState.Error)
        }
        captured = EditableTarget.snapshot(node)
        session = engine.start().also {
            it.setLevelListener(onLevel)
            if (partialsEnabled()) {
                it.setPartialListener { text ->
                    val speech = TranscriptNoise.usableSpeech(text) ?: return@setPartialListener
                    mainHandler.post { onPartial(speech) }
                }
            }
        }
        publish(DictationState.Recording)
    }

    private fun stop() {
        val active = session ?: return publish(DictationState.Error)
        val expected = captured
        val generation = operationGeneration
        session = null
        finishingSession = active
        publish(DictationState.Processing)
        background.execute {
            var failureMessage: String? = null
            var failureType: String? = null
            val transcript = try {
                active.finish()
            } catch (error: Throwable) {
                failureType = error.javaClass.simpleName
                failureMessage = error.message ?: failureType
                null
            }
            val outcome = if (failureMessage != null) {
                InsertionOutcome.Failed
            } else {
                finishTranscript(expected, transcript.orEmpty(), generation)
            }
            mainHandler.post {
                if (generation != operationGeneration || state != DictationState.Processing) {
                    DictationDiagnostics.record(
                        "completion_discarded cycle=$generation currentCycle=$operationGeneration state=$state",
                    )
                    return@post
                }
                if (outcome == InsertionOutcome.Failed && failureMessage != null) {
                    DictationDiagnostics.record(
                        "transcription_failed cycle=$generation error=$failureType",
                    )
                    onError("Transcription failed: $failureMessage")
                }
                if (outcome != InsertionOutcome.Direct) {
                    DictationDiagnostics.record(
                        "insertion_outcome cycle=$generation outcome=$outcome " +
                            "target=${DictationDiagnostics.snapshot(expected)}",
                    )
                }
                completeStop(outcome)
            }
        }
    }

    /** Returns null when the transcript is empty: an empty result is not a failure. */
    private fun finishTranscript(
        expected: TargetSnapshot?,
        transcript: String,
        generation: Long,
    ): InsertionOutcome? {
        val speech = TranscriptNoise.usableSpeech(transcript) ?: run {
            DictationDiagnostics.record("discarded_noise cycle=$generation")
            return null
        }
        if (expected == null) return inserter.copyToClipboard(speech)
        val node = resolveFreshNode(expected)
        return if (node == null) {
            DictationDiagnostics.record(
                "target_unavailable cycle=$generation expected=${DictationDiagnostics.snapshot(expected)}",
            )
            inserter.copyToClipboard(speech)
        } else {
            inserter.insert(node, expected, speech, replaceAll())
        }
    }

    private fun completeStop(outcome: InsertionOutcome?) {
        finishingSession = null
        captured = null
        when (outcome) {
            null -> publish(DictationState.Idle)
            InsertionOutcome.Direct -> {
                publish(DictationState.Done)
                scheduleReset(DONE_MS)
            }
            InsertionOutcome.ClipboardFallback -> {
                onError("Couldn't insert the text — it's on the clipboard")
                publish(DictationState.Done)
                scheduleReset(DONE_MS)
            }
            InsertionOutcome.Failed -> {
                onError("Could not insert the text into the field")
                publish(DictationState.Error)
                scheduleReset(ERROR_MS)
            }
        }
    }

    private fun scheduleReset(delayMs: Long) {
        cancelPendingReset()
        val generation = operationGeneration
        pendingReset = Runnable {
            if (generation != operationGeneration) return@Runnable
            pendingReset = null
            reset()
        }.also { mainHandler.postDelayed(it, delayMs) }
    }

    private fun cancelPendingReset() {
        pendingReset?.let { mainHandler.removeCallbacks(it) }
        pendingReset = null
    }

    private fun publish(next: DictationState) {
        state = next
        onState(next)
    }

    companion object {
        private const val DONE_MS = 700L
        private const val ERROR_MS = 1400L
    }
}
