package io.jyri.dictator.model

import android.content.Context

/** Persists the language allow-list used by multilingual model detection. */
object LanguageSelection {
    private const val PREFS = "spoken_languages"
    private const val KEY_CODES = "codes"

    val default: Set<SpokenLanguage> = setOf(
        SpokenLanguage.ENGLISH,
        SpokenLanguage.FINNISH,
    )

    fun load(context: Context): Set<SpokenLanguage> {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_CODES, null)
            ?: return default
        val selected = SpokenLanguage.entries
            .filter { it.whisperCode in stored }
            .toCollection(LinkedHashSet())
        return selected.ifEmpty { default }
    }

    fun loadFor(context: Context, model: SttModelProfile): Set<SpokenLanguage> =
        if (model.isMultilingual) load(context) else emptySet()

    fun store(context: Context, languages: Set<SpokenLanguage>) {
        require(languages.isNotEmpty()) { "At least one spoken language is required" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_CODES, languages.mapTo(linkedSetOf()) { it.whisperCode })
            .apply()
    }
}
