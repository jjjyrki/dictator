package io.jyri.dictator

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.ApplicationInfo
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import io.jyri.dictator.focus.EditableTarget
import io.jyri.dictator.focus.findFocusedInput
import io.jyri.dictator.insert.AccessibilityImeTyper
import io.jyri.dictator.insert.InsertMode
import io.jyri.dictator.insert.TextInserter
import io.jyri.dictator.model.FinnishSetting
import io.jyri.dictator.model.ModelSelection
import io.jyri.dictator.model.SttModelInstaller
import io.jyri.dictator.overlay.BubbleOverlay
import io.jyri.dictator.session.DictationSessionController
import io.jyri.dictator.session.DictationState
import io.jyri.dictator.speech.PartialsSetting
import io.jyri.dictator.speech.SttEngineHolder
import io.jyri.dictator.speech.WhisperLiveSpeechEngine
import io.jyri.dictator.speech.WhisperSttEngine
import java.util.concurrent.Executors

class DictationAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val focusRetry = Runnable {
        // Compose can leave findFocus pointing at a stale virtual node after a transition.
        clearCache()
        refreshBubble(allowRetry = false)
    }
    private var modelMissingWarned = false
    private var bubble: BubbleOverlay? = null
    private var controller: DictationSessionController? = null

    override fun onServiceConnected() {
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_INPUT_METHOD_EDITOR
        }
        ensureEngineLoaded()
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val overlay = BubbleOverlay(
            windowManager = windowManager,
            inflater = LayoutInflater.from(this),
            onTap = { controller?.onTap() },
            onCancel = { controller?.reset() },
        )
        bubble = overlay
        controller = DictationSessionController(
            speechSessionFactory = {
                SttEngineHolder.engine?.let { shared ->
                    WhisperLiveSpeechEngine(shared, partialsEnabled = PartialsSetting.load(this))
                }.also { if (it == null) ensureEngineLoaded() }
            },
            inserter = TextInserter(
                this,
                AccessibilityImeTyper(
                    inputConnection = { inputMethod?.currentInputConnection },
                    mainHandler = mainHandler,
                ),
            ),
            resolveFreshNode = { expected -> currentEditableFocus(expected?.windowId) },
            onState = { state ->
                if (state == DictationState.Idle && currentEditableFocus() == null) {
                    overlay.hide()
                } else {
                    overlay.show(state)
                    overlay.update(state)
                }
            },
            onLevel = { level -> mainHandler.post { bubble?.setLevel(level) } },
            onPartial = { text -> bubble?.showPartial(text) },
            onError = { message ->
                mainHandler.post { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
            },
            replaceAll = { InsertMode.load(this) == InsertMode.REPLACE },
            background = Executors.newSingleThreadExecutor(),
        )
        refreshBubble()
    }

    /**
     * Loads the persisted model in the background if the process lost its
     * engine, so the bubble works without opening the app first.
     */
    private fun ensureEngineLoaded() {
        val variant = ModelSelection.load(this)
        val finnish = FinnishSetting.load(this)
        if (SttEngineHolder.matches(variant, finnish)) return
        SttEngineHolder.clear()
        val installer = SttModelInstaller(this, variant, finnish)
        if (!installer.isInstalled()) {
            if (!modelMissingWarned) {
                modelMissingWarned = true
                mainHandler.post {
                    Toast.makeText(
                        this,
                        getString(R.string.model_missing_toast),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
            return
        }
        Thread {
            runCatching {
                SttEngineHolder.install(
                    variant,
                    finnish,
                    WhisperSttEngine(installer.modelFile(), installer.language()),
                )
            }
        }.start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            -> {
                logFocusDiagnostics(event)
                refreshBubble()
            }
            else -> Unit
        }
    }

    private fun logFocusDiagnostics(event: AccessibilityEvent) {
        // Opt in with: adb shell setprop log.tag.DictatorFocus DEBUG
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0 ||
            !Log.isLoggable("DictatorFocus", Log.DEBUG)
        ) return
        val root = rootInActiveWindow
        Log.d(
            "DictatorFocus",
            "event=${AccessibilityEvent.eventTypeToString(event.eventType)} " +
                "package=${event.packageName} class=${event.className} " +
                "source=${describeFocusNode(event.source)} " +
                "input=${describeFocusNode(root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT))} " +
                "accessibility=${describeFocusNode(root?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY))}",
        )
    }

    private fun describeFocusNode(node: AccessibilityNodeInfo?): String {
        if (node == null) return "null"
        // Never log text, hints, descriptions, or action labels from another app.
        return "[class=${node.className}, editable=${node.isEditable}, focused=${node.isFocused}, " +
            "visible=${node.isVisibleToUser}, enabled=${node.isEnabled}, password=${node.isPassword}, " +
            "usable=${EditableTarget.isUsable(node)}, actions=${node.actionList.map { it.id }}]"
    }

    override fun onInterrupt() {
        controller?.reset()
        mainHandler.removeCallbacks(focusRetry)
        bubble?.hide()
    }

    override fun onDestroy() {
        controller?.reset()
        mainHandler.removeCallbacks(focusRetry)
        bubble?.hide()
        bubble = null
        controller = null
        super.onDestroy()
    }

    private fun refreshBubble(allowRetry: Boolean = true) {
        val overlay = bubble ?: return
        val session = controller ?: return
        if (session.state != DictationState.Idle) return
        val node = currentEditableFocus()
        if (node == null) {
            overlay.hide()
            // One pending retry; content-event bursts must not postpone it indefinitely.
            if (allowRetry && !mainHandler.hasCallbacks(focusRetry)) {
                mainHandler.postDelayed(focusRetry, 150L)
            }
        } else {
            mainHandler.removeCallbacks(focusRetry)
            overlay.show(DictationState.Idle)
        }
    }

    /**
     * Finds the focused editable field. While an IME is open the active
     * window can be the keyboard itself, so at finish time the recorded
     * target window is searched explicitly before giving up.
     */
    private fun currentEditableFocus(expectedWindowId: Int? = null): AccessibilityNodeInfo? {
        currentEditableFocusIn(rootInActiveWindow)?.let { return it }
        if (expectedWindowId == null) return null
        for (window in windows) {
            if (window.id != expectedWindowId) continue
            currentEditableFocusIn(window.root)?.let { return it }
        }
        return null
    }

    private fun currentEditableFocusIn(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused?.refresh() == true && focused.isFocused && EditableTarget.isUsable(focused)) {
            return focused
        }
        // Do not choose the first editable field: it must carry actual input focus.
        findFocusedInput(
            root = root,
            children = { node -> (0 until node.childCount).asSequence().mapNotNull(node::getChild) },
            isFocused = { it.isFocused },
            isUsable = { it.refresh() && it.isFocused && EditableTarget.isUsable(it) },
        )?.let { return it }
        val accessibilityFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        if (accessibilityFocus?.refresh() == true && EditableTarget.isUsable(accessibilityFocus)) {
            return accessibilityFocus
        }
        return null
    }
}
