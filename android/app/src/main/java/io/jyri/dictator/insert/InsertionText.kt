package io.jyri.dictator.insert

object InsertionText {
    /**
     * Whisper segment text often begins with a space. Keep it as a word
     * separator when text precedes the insertion point; drop it when the
     * insertion lands at the start of the field (including an empty field).
     */
    fun withSeparator(prefix: String, insert: String): String =
        if (prefix.isBlank()) insert.trimStart() else insert

    fun apply(
        current: String,
        selectionStart: Int,
        selectionEnd: Int,
        insert: String,
    ): Pair<String, Int> {
        val start = selectionStart.coerceIn(0, current.length)
        val rawEnd = selectionEnd.coerceIn(0, current.length)
        val end = maxOf(start, rawEnd)
        val cleaned = withSeparator(current.substring(0, start), insert)
        val next = current.substring(0, start) + cleaned + current.substring(end)
        return next to (start + cleaned.length)
    }
}
