package io.jyri.dictator.model

import android.content.Context

/**
 * When on, the selected size tier loads FUTO's multilingual ACFT file and
 * transcribes as Finnish. English-only `_en` weights cannot do Finnish.
 */
object FinnishSetting {
    private const val PREFS = "finnish"
    private const val KEY_ENABLED = "enabled"

    fun load(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun store(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}
