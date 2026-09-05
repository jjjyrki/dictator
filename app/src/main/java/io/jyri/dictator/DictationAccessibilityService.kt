package io.jyri.dictator

import android.accessibilityservice.AccessibilityService
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import io.jyri.dictator.focus.EditableTarget
import io.jyri.dictator.insert.TextInserter
import io.jyri.dictator.overlay.BubbleOverlay
import io.jyri.dictator.session.DictationSessionController
import io.jyri.dictator.session.DictationState
import io.jyri.dictator.speech.StubSpeechEngine

class DictationAccessibilityService : AccessibilityService() {
    private var bubble: BubbleOverlay? = null
    private var controller: DictationSessionController? = null

    override fun onServiceConnected() {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val overlay = BubbleOverlay(
            windowManager = windowManager,
            inflater = LayoutInflater.from(this),
            onTap = { controller?.onTap() },
        )
        bubble = overlay
        controller = DictationSessionController(
            speechEngine = StubSpeechEngine(),
            inserter = TextInserter(this),
            resolveFreshNode = { currentEditableFocus() },
            onState = { state ->
                if (state == DictationState.Idle && currentEditableFocus() == null) {
                    overlay.hide()
                } else {
                    overlay.show(state)
                    overlay.update(state)
                }
            },
        )
        refreshBubble()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            -> refreshBubble()
        }
    }

    override fun onInterrupt() {
        controller?.reset()
        bubble?.hide()
    }

    override fun onDestroy() {
        controller?.reset()
        bubble?.hide()
        bubble = null
        controller = null
        super.onDestroy()
    }

    private fun refreshBubble() {
        val overlay = bubble ?: return
        val session = controller ?: return
        if (session.state != DictationState.Idle) return
        val node = currentEditableFocus()
        if (node == null) {
            overlay.hide()
        } else {
            overlay.show(DictationState.Idle)
        }
    }

    private fun currentEditableFocus(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (EditableTarget.isUsable(focused)) return focused
        val accessibilityFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        if (EditableTarget.isUsable(accessibilityFocus)) return accessibilityFocus
        return null
    }
}
