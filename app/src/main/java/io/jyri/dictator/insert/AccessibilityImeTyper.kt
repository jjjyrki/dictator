package io.jyri.dictator.insert

import android.accessibilityservice.InputMethod.AccessibilityInputConnection
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Types through the accessibility service's InputConnection. This is not an
 * InputMethodService and does not replace the current keyboard.
 */
class AccessibilityImeTyper(
    private val inputConnection: () -> AccessibilityInputConnection?,
    private val mainHandler: Handler,
) : TextInserter.TypeIntoFocusedEditor {
    override fun type(text: String, replaceAll: Boolean): Boolean {
        if (text.isEmpty()) return false
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return commit(text, replaceAll)
        }
        val ok = AtomicBoolean(false)
        val done = CountDownLatch(1)
        if (!mainHandler.post {
                try {
                    ok.set(commit(text, replaceAll))
                } finally {
                    done.countDown()
                }
            }
        ) {
            return false
        }
        return runCatching { done.await(IME_TIMEOUT_MS, TimeUnit.MILLISECONDS) }.getOrDefault(false) &&
            ok.get()
    }

    private fun commit(text: String, replaceAll: Boolean): Boolean {
        val ic = inputConnection() ?: return false
        return runCatching {
            if (replaceAll) {
                ic.performContextMenuAction(android.R.id.selectAll)
            }
            val prefix = if (replaceAll) "" else textBeforeCursor(ic)
            ic.commitText(InsertionText.withSeparator(prefix, text), 1, null)
            true
        }.getOrDefault(false)
    }

    private fun textBeforeCursor(ic: AccessibilityInputConnection): String {
        val surrounding = ic.getSurroundingText(1, 0, 0) ?: return ""
        val text = surrounding.text?.toString().orEmpty()
        val start = surrounding.selectionStart.coerceIn(0, text.length)
        return text.substring(0, start)
    }

    private companion object {
        const val IME_TIMEOUT_MS = 2_000L
    }
}
