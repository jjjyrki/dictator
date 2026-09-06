package io.jyri.dictator.speech

import android.content.Context

/**
 * Live partial transcriptions shown in the bubble while recording. Partials
 * re-run Whisper on the buffered audio every couple of seconds, so they cost
 * extra battery; they can be toggled off in the setup screen.
 */
object PartialsSetting {
    private const val PREFS = "partials"
    private const val KEY_ENABLED = "enabled"
    private const val DEFAULT = true

    fun load(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, DEFAULT)

    fun store(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}
