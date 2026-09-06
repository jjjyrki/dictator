package io.jyri.dictator.model

import android.content.Context

/**
 * Legacy preference retained only to migrate pre-language-selector installs.
 */
@Deprecated("Used only when migrating the legacy model preference")
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
