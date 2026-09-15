package io.jyri.dictator.model

import android.content.Context

/** Persists the complete checkpoint family and size choice. */
object ModelSelection {
    private const val PREFS = "model_selection"
    private const val KEY_PROFILE = "profile"
    private const val LEGACY_KEY_VARIANT = "variant"

    val default: SttModelProfile = SttModelProfile.default

    @Suppress("DEPRECATION")
    fun load(context: Context): SttModelProfile {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PROFILE, null)
        SttModelProfile.entries.firstOrNull { it.name == stored }?.let { return it }

        // Preserve installations created before model family became part of the
        // selection. The old Finnish preference identified the family.
        val legacyVariant = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(LEGACY_KEY_VARIANT, null)
            ?.let { name -> SttModelVariant.entries.firstOrNull { it.name == name } }
        return legacyVariant?.let { SttModelProfile.fromLegacy(it, FinnishSetting.load(context)) } ?: default
    }

    fun store(context: Context, profile: SttModelProfile) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROFILE, profile.name)
            .apply()
    }
}
