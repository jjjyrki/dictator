package io.jyri.dictator.insert

object InsertionText {
    fun apply(
        current: String,
        selectionStart: Int,
        selectionEnd: Int,
        insert: String,
    ): Pair<String, Int> {
        val start = selectionStart.coerceIn(0, current.length)
        val rawEnd = selectionEnd.coerceIn(0, current.length)
        val end = maxOf(start, rawEnd)
        val next = current.substring(0, start) + insert + current.substring(end)
        return next to (start + insert.length)
    }
}
