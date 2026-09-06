package io.jyri.dictator.model

import android.content.Context

/** Persists the user's model choice; defaults to the most accurate model. */
object ModelSelection {
    private const val PREFS = "model_selection"
    private const val KEY_NAME = "variant"

    val default: SttModelVariant = SttModelVariant.SMALL

    fun load(context: Context): SttModelVariant {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = stored.getString(KEY_NAME, null) ?: return default
        return SttModelVariant.entries.firstOrNull { it.name == name } ?: default
    }

    fun store(context: Context, variant: SttModelVariant) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NAME, variant.name)
            .apply()
    }
}
