package io.jyri.dictator.insert

/**
 * Whip's xterm WebView does not turn ACTION_SET_TEXT into terminal input.
 * Prefer the accessibility IME (same path as Samsung Keyboard). Clipboard
 * paste is the fallback and shows Android's "Copied" overlay.
 */
internal object WhipInsertion {
    fun insert(
        transcript: String,
        @Suppress("UNUSED_PARAMETER") replaceAll: Boolean,
        typeIntoEditor: (text: String, replaceAll: Boolean) -> Boolean,
        paste: () -> Boolean,
        copy: () -> Boolean,
    ): InsertionOutcome {
        // A terminal is a stream, not a field. Replace-all would select or
        // delete the whole buffer.
        if (typeIntoEditor(prepareTypedText(transcript), false)) {
            return InsertionOutcome.Direct
        }
        if (paste()) return InsertionOutcome.Direct
        return if (copy()) InsertionOutcome.ClipboardFallback else InsertionOutcome.Failed
    }

    /** Match Whip's xterm paste: newlines become carriage returns. */
    fun prepareTypedText(text: String): String =
        text.replace("\r\n", "\r").replace('\n', '\r')
}
