package io.jyri.dictator.insert

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import io.jyri.dictator.DictationDiagnostics
import io.jyri.dictator.focus.EditableTarget
import io.jyri.dictator.focus.TargetSnapshot

class TextInserter(
    private val clipboardManager: ClipboardManager?,
    private val typeIntoFocusedEditor: TypeIntoFocusedEditor? = null,
) {
    constructor(
        context: Context,
        typeIntoFocusedEditor: TypeIntoFocusedEditor? = null,
    ) : this(
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager,
        typeIntoFocusedEditor,
    )

    fun interface TypeIntoFocusedEditor {
        fun type(text: String, replaceAll: Boolean): Boolean
    }

    fun insert(
        node: AccessibilityNodeInfo,
        expected: TargetSnapshot,
        transcript: String,
        replaceAll: Boolean = false,
    ): InsertionOutcome {
        if (!EditableTarget.matches(expected, node)) {
            DictationDiagnostics.record(
                "target_mismatch expected=${DictationDiagnostics.snapshot(expected)}",
            )
            return clipboardOnly(transcript)
        }
        if (expected.packageName == WHIP_PACKAGE) {
            return WhipInsertion.insert(
                transcript = transcript,
                replaceAll = replaceAll,
                typeIntoEditor = { text, replace ->
                    typeIntoFocusedEditor?.type(text, replace) == true
                },
                paste = { paste(node, transcript) },
                copy = { copy(transcript) },
            )
        }
        val current = node.actualText()
        if (replaceAll) {
            return replaceContent(node, transcript)
        }
        val start = node.textSelectionStart
        val end = node.textSelectionEnd
        val selectionStart = if (start < 0) current.length else start
        val selectionEnd = if (end < 0) selectionStart else end
        val cleaned = InsertionText.withSeparator(
            current.substring(0, selectionStart.coerceIn(0, current.length)),
            transcript,
        )
        val (next, cursor) = InsertionText.apply(current, selectionStart, selectionEnd, cleaned)
        val setText = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, next)
        }
        val setTextAccepted = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setText)
        if (setTextAccepted) {
            if (!verifyText(node, next)) {
                DictationDiagnostics.record(
                    "set_text_unverified expectedLength=${next.length} target=${DictationDiagnostics.snapshot(expected)}",
                )
                return if (copy(transcript)) InsertionOutcome.ClipboardFallback else InsertionOutcome.Failed
            }
            val selection = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
            }
            val selectionAccepted = node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection)
            if (!selectionAccepted) DictationDiagnostics.record("selection_rejected")
            return InsertionOutcome.Direct
        }
        DictationDiagnostics.record(
            "set_text_rejected target=${DictationDiagnostics.snapshot(expected)}",
        )
        val pasted = paste(node, cleaned)
        if (pasted) return InsertionOutcome.Direct
        val copied = copy(transcript)
        return if (copied) InsertionOutcome.ClipboardFallback else InsertionOutcome.Failed
    }

    private fun replaceContent(node: AccessibilityNodeInfo, transcript: String): InsertionOutcome {
        // Whole-field replacement never appends, so no leading separator.
        val text = InsertionText.withSeparator("", transcript)
        val setText = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val setTextAccepted = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setText)
        if (setTextAccepted) {
            if (!verifyText(node, text)) {
                DictationDiagnostics.record(
                    "replace_unverified expectedLength=${text.length}",
                )
                return if (copy(transcript)) InsertionOutcome.ClipboardFallback else InsertionOutcome.Failed
            }
            val selection = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, text.length)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, text.length)
            }
            val selectionAccepted = node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection)
            if (!selectionAccepted) DictationDiagnostics.record("replace_selection_rejected")
            return InsertionOutcome.Direct
        }
        DictationDiagnostics.record("replace_set_text_rejected")
        val copied = copy(transcript)
        return if (copied) InsertionOutcome.ClipboardFallback else InsertionOutcome.Failed
    }

    fun copyToClipboard(transcript: String): InsertionOutcome =
        if (copy(transcript)) InsertionOutcome.ClipboardFallback else InsertionOutcome.Failed

    private fun clipboardOnly(transcript: String): InsertionOutcome = copyToClipboard(transcript)

    private fun paste(node: AccessibilityNodeInfo, transcript: String): Boolean =
        copy(transcript) && node.performAction(AccessibilityNodeInfo.ACTION_PASTE)

    private fun verifyText(node: AccessibilityNodeInfo, expected: String): Boolean {
        val refreshed = node.refresh()
        val observed = node.actualText()
        return refreshed && observed == expected
    }

    private fun copy(transcript: String): Boolean {
        val clipboard = clipboardManager ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText("dictator", transcript))
        return true
    }

    private companion object {
        const val WHIP_PACKAGE = "io.github.kaminarios.whip"
    }
}
