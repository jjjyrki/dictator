package io.jyri.dictator.insert

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import io.jyri.dictator.focus.EditableTarget
import io.jyri.dictator.focus.TargetSnapshot

class TextInserter(
    private val clipboardManager: ClipboardManager?,
) {
    constructor(context: Context) : this(
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager,
    )

    fun insert(
        node: AccessibilityNodeInfo,
        expected: TargetSnapshot,
        transcript: String,
    ): InsertionOutcome {
        if (!EditableTarget.matches(expected, node)) {
            return clipboardOnly(transcript)
        }
        val current = node.text?.toString().orEmpty()
        val start = node.textSelectionStart
        val end = node.textSelectionEnd
        val selectionStart = if (start < 0) current.length else start
        val selectionEnd = if (end < 0) selectionStart else end
        val (next, cursor) = InsertionText.apply(current, selectionStart, selectionEnd, transcript)
        val setText = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, next)
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setText)) {
            val selection = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection)
            return InsertionOutcome.Direct
        }
        if (copy(transcript) && node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
            return InsertionOutcome.ClipboardFallback
        }
        return if (copy(transcript)) InsertionOutcome.ClipboardFallback else InsertionOutcome.Failed
    }

    private fun clipboardOnly(transcript: String): InsertionOutcome {
        return if (copy(transcript)) InsertionOutcome.ClipboardFallback else InsertionOutcome.Failed
    }

    private fun copy(transcript: String): Boolean {
        val clipboard = clipboardManager ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText("dictator", transcript))
        return true
    }
}
