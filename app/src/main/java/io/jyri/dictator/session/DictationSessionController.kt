package io.jyri.dictator.session

import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import io.jyri.dictator.focus.EditableTarget
import io.jyri.dictator.focus.TargetSnapshot
import io.jyri.dictator.insert.InsertionOutcome
import io.jyri.dictator.insert.TextInserter
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
    private val replaceAll: () -> Boolean = { false },
    private val partialsEnabled: () -> Boolean = { false },
    private val background: Executor = Executor { it.run() },
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) {
    var state: DictationState = DictationState.Idle
        private set

    private var captured: TargetSnapshot? = null
    private var session: SpeechSession? = null

    fun onTap() {
        when (state) {
            DictationState.Idle -> start()
            DictationState.Recording -> stop()
            DictationState.Processing -> Unit
            DictationState.Done, DictationState.Error -> reset()
        }
    }

    fun reset() {
        session?.cancel()
        session = null
        captured = null
        publish(DictationState.Idle)
    }

    private fun start() {
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
                it.setPartialListener { text -> mainHandler.post { onPartial(text) } }
            }
        }
        publish(DictationState.Recording)
    }

    private fun stop() {
        val active = session ?: return publish(DictationState.Error)
        val expected = captured
        session = null
        publish(DictationState.Processing)
        background.execute {
            var failureMessage: String? = null
            val transcript = try {
                active.finish()
            } catch (error: Throwable) {
                failureMessage = error.message ?: error.javaClass.simpleName
                null
            }
            val outcome = transcript
                ?.let { text -> finishTranscript(expected, text) }
                ?: InsertionOutcome.Failed
            if (outcome == InsertionOutcome.Failed && failureMessage != null) {
                onError("Transcription failed: $failureMessage")
            }
            mainHandler.post { completeStop(outcome) }
        }
    }

    /** Returns null when the transcript is empty: an empty result is not a failure. */
    private fun finishTranscript(expected: TargetSnapshot?, transcript: String): InsertionOutcome? {
        if (transcript.isEmpty()) return null
        if (expected == null) return inserter.copyToClipboard(transcript)
        val node = resolveFreshNode(expected)
        return if (node == null) {
            inserter.copyToClipboard(transcript)
        } else {
            inserter.insert(node, expected, transcript, replaceAll())
        }
    }

    private fun completeStop(outcome: InsertionOutcome?) {
        captured = null
        when (outcome) {
            null -> publish(DictationState.Idle)
            InsertionOutcome.Direct -> {
                publish(DictationState.Done)
                mainHandler.postDelayed({ reset() }, DONE_MS)
            }
            InsertionOutcome.ClipboardFallback -> {
                onError("Couldn't insert the text — it's on the clipboard")
                publish(DictationState.Done)
                mainHandler.postDelayed({ reset() }, DONE_MS)
            }
            InsertionOutcome.Failed -> {
                onError("Could not insert the text into the field")
                publish(DictationState.Error)
                mainHandler.postDelayed({ reset() }, ERROR_MS)
            }
        }
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
