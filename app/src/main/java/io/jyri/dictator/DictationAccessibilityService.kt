package io.jyri.dictator

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.ApplicationInfo
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.content.pm.PackageManager
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
import io.jyri.dictator.model.LanguageSelection
import io.jyri.dictator.model.ModelSelection
import io.jyri.dictator.model.SttModelInstaller
import io.jyri.dictator.overlay.BubbleOverlay
import io.jyri.dictator.session.DictationSessionController
import io.jyri.dictator.session.DictationState
import io.jyri.dictator.speech.PartialsSetting
import io.jyri.dictator.speech.SttEngineHolder
import io.jyri.dictator.speech.WhisperLanguageConfig
import io.jyri.dictator.speech.WhisperLiveSpeechEngine
import io.jyri.dictator.speech.WhisperSttEngine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class DictationAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val background: ExecutorService = Executors.newSingleThreadExecutor()
    private val engineLoadInProgress = AtomicBoolean(false)
    private val scheduledBubbleRefresh = Runnable { refreshBubble() }
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
                if (state == DictationState.Idle) {
                    refreshBubble()
                } else {
                    // A focus retry belongs to the idle visibility decision. Do
                    // not let it run after recording has started and affect the
                    // next state transition.
                    mainHandler.removeCallbacks(focusRetry)
                    overlay.show(state)
                }
            },
            onLevel = { level -> mainHandler.post { bubble?.setLevel(level) } },
            onPartial = { text -> bubble?.showPartial(text) },
            onError = { message ->
                mainHandler.post { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
            },
            microphonePermissionGranted = ::hasMicrophonePermission,
            onMicrophonePermissionRequired = ::openMicrophonePermission,
            replaceAll = { InsertMode.load(this) == InsertMode.REPLACE },
            background = background,
        )
        refreshBubble()
    }

    /**
     * Loads the persisted model in the background if the process lost its
     * engine, so the bubble works without opening the app first.
     */
    private fun ensureEngineLoaded() {
        val model = ModelSelection.load(this)
        val languages = LanguageSelection.loadFor(this, model)
        if (SttEngineHolder.matches(model, languages)) return
        SttEngineHolder.clear()
        val installer = SttModelInstaller(this, model.asset)
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
        if (!engineLoadInProgress.compareAndSet(false, true)) return
        background.execute {
            try {
                runCatching {
                    SttEngineHolder.install(
                        model,
                        languages,
                        WhisperSttEngine(
                            installer.modelFile(),
                            WhisperLanguageConfig.forModel(model, languages),
                        ),
                    )
                }
            } finally {
                engineLoadInProgress.set(false)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            -> {
                logFocusDiagnostics(event)
                scheduleBubbleRefresh()
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
        mainHandler.removeCallbacks(scheduledBubbleRefresh)
        mainHandler.removeCallbacks(focusRetry)
        bubble?.hide()
    }

    override fun onDestroy() {
        controller?.reset()
        mainHandler.removeCallbacks(scheduledBubbleRefresh)
        mainHandler.removeCallbacks(focusRetry)
        bubble?.hide()
        bubble = null
        controller = null
        background.shutdownNow()
        super.onDestroy()
    }

    private fun scheduleBubbleRefresh() {
        if (!mainHandler.hasCallbacks(scheduledBubbleRefresh)) {
            mainHandler.postDelayed(scheduledBubbleRefresh, BUBBLE_REFRESH_DEBOUNCE_MS)
        }
    }

    private fun refreshBubble(allowRetry: Boolean = true) {
        val overlay = bubble ?: return
        val session = controller ?: return
        val permissionGranted = hasMicrophonePermission()
        if (session.state == DictationState.MicrophonePermissionRequired && permissionGranted) {
            session.reset()
            return
        }
        if (session.state != DictationState.Idle &&
            session.state != DictationState.MicrophonePermissionRequired
        ) return
        val node = currentEditableFocus()
        if (node == null) {
            if (allowRetry) {
                // Keep the current bubble through the short period where an app
                // is rebuilding its accessibility tree. Hide only after a fresh
                // lookup confirms that focus is really gone.
                if (!mainHandler.hasCallbacks(focusRetry)) {
                    mainHandler.postDelayed(focusRetry, FOCUS_RETRY_MS)
                }
            } else {
                overlay.hide()
            }
        } else {
            mainHandler.removeCallbacks(focusRetry)
            overlay.show(
                if (permissionGranted) {
                    DictationState.Idle
                } else {
                    DictationState.MicrophonePermissionRequired
                },
            )
        }
    }

    private fun hasMicrophonePermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun openMicrophonePermission() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
                putExtra(MainActivity.EXTRA_REQUEST_MICROPHONE_PERMISSION, true)
            },
        )
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

    private companion object {
        const val BUBBLE_REFRESH_DEBOUNCE_MS = 50L
        const val FOCUS_RETRY_MS = 150L
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
