package io.jyri.dictator.insert

/**
 * Whisper often emits bracket tags instead of speech when the clip is
 * silence or music. Same rules as macOS `TranscriptNoise`.
 */
object TranscriptNoise {
    private val tags = setOf(
        "BLANK_AUDIO",
        "BLANK AUDIO",
        "MUSIC",
        "SILENCE",
        "NOISE",
        "INAUDIBLE",
        "COUGH",
        "COUGHING",
        "APPLAUSE",
        "LAUGHTER",
        "HUMMING",
        "SINGING",
        "STATIC",
        "TYPING",
        "BLANK",
    )

    private val tokenPattern = Regex("""\[([^\[\]]+)\]|\(([^()]+)\)""")
    private val extraSpace = Regex("""\s+""")

    /** Speech left after dropping silence/music tags, or null if nothing usable remains. */
    fun usableSpeech(raw: String): String? {
        var text = raw.trim()
        if (text.isEmpty()) return null

        val matches = tokenPattern.findAll(text).toList()
        for (match in matches.asReversed()) {
            val inner = match.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() }
                ?: match.groupValues.getOrNull(2).orEmpty()
            if (isNoiseTag(inner)) {
                text = text.replaceRange(match.range, " ")
            }
        }

        text = extraSpace.replace(text.replace("♪", " "), " ").trim()
        if (text.isEmpty()) return null

        val compact = text.replace("_", " ").trim()
        if (isNoiseTag(compact)) return null

        val leftover = text.filter { !it.isWhitespace() && !it.isPunctuationLike() }
        if (leftover.isEmpty()) return null
        return text
    }

    private fun isNoiseTag(raw: String): Boolean {
        val folded = raw.trim().replace("_", " ").uppercase()
        if (folded in tags) return true
        val squeezed = folded.replace(" ", "")
        return squeezed == "BLANKAUDIO" || squeezed in tags
    }

    private fun Char.isPunctuationLike(): Boolean {
        val type = Character.getType(this)
        return type == Character.CONNECTOR_PUNCTUATION.toInt() ||
            type == Character.DASH_PUNCTUATION.toInt() ||
            type == Character.START_PUNCTUATION.toInt() ||
            type == Character.END_PUNCTUATION.toInt() ||
            type == Character.INITIAL_QUOTE_PUNCTUATION.toInt() ||
            type == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
            type == Character.OTHER_PUNCTUATION.toInt()
    }
}
