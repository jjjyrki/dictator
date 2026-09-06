package io.jyri.dictator.insert

import android.content.Context

/**
 * Bubble insertion mode. `MERGE` inserts at the cursor and protects drafts.
 * `REPLACE` overwrites the whole field, for apps that keep their placeholder
 * as real text (Google search bar and some chat command boxes).
 */
enum class InsertMode {
    MERGE,
    REPLACE,
    ;

    companion object {
        private const val PREFS = "insert_mode"
        private const val KEY_MODE = "mode"

        fun load(context: Context): InsertMode {
            val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_MODE, null)
            return entries.firstOrNull { it.name == stored } ?: MERGE
        }

        fun store(context: Context, mode: InsertMode) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_MODE, mode.name)
                .apply()
        }
    }
}
