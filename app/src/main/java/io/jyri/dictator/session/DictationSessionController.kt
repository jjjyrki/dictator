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

class DictationSessionController(
    private val speechEngine: SpeechEngine,
    private val inserter: TextInserter,
    private val resolveFreshNode: () -> AccessibilityNodeInfo?,
    private val onState: (DictationState) -> Unit,
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
        val node = resolveFreshNode() ?: return publish(DictationState.Error)
        if (!EditableTarget.isUsable(node)) return publish(DictationState.Error)
        captured = EditableTarget.snapshot(node)
        session = speechEngine.start()
        publish(DictationState.Recording)
    }

    private fun stop() {
        val active = session ?: return publish(DictationState.Error)
        val expected = captured ?: return publish(DictationState.Error)
        publish(DictationState.Processing)
        val transcript = active.finish()
        session = null
        if (transcript.isEmpty()) {
            captured = null
            publish(DictationState.Idle)
            return
        }
        val node = resolveFreshNode()
        val outcome = if (node == null) {
            InsertionOutcome.Failed
        } else {
            inserter.insert(node, expected, transcript)
        }
        captured = null
        when (outcome) {
            InsertionOutcome.Direct -> {
                publish(DictationState.Done)
                mainHandler.postDelayed({ reset() }, DONE_MS)
            }
            InsertionOutcome.ClipboardFallback, InsertionOutcome.Failed -> {
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
