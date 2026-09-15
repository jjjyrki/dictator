package io.jyri.dictator.model

/** The downloaded checkpoint family. */
enum class SttModelFamily {
    ENGLISH_ONLY,
    MULTILINGUAL,
}

/** A complete user-selectable model choice, including its checkpoint family. */
enum class SttModelProfile(
    val variant: SttModelVariant,
    val family: SttModelFamily,
) {
    ENGLISH_TINY(SttModelVariant.TINY, SttModelFamily.ENGLISH_ONLY),
    ENGLISH_BASE(SttModelVariant.BASE, SttModelFamily.ENGLISH_ONLY),
    ENGLISH_SMALL(SttModelVariant.SMALL, SttModelFamily.ENGLISH_ONLY),
    MULTILINGUAL_TINY(SttModelVariant.TINY, SttModelFamily.MULTILINGUAL),
    MULTILINGUAL_BASE(SttModelVariant.BASE, SttModelFamily.MULTILINGUAL),
    MULTILINGUAL_SMALL(SttModelVariant.SMALL, SttModelFamily.MULTILINGUAL),
    ;

    val asset: SttModelAsset
        get() = variant.asset(family)

    val displayName: String
        get() = asset.displayName

    val isMultilingual: Boolean
        get() = family == SttModelFamily.MULTILINGUAL

    companion object {
        val default: SttModelProfile = MULTILINGUAL_SMALL

        fun fromLegacy(variant: SttModelVariant, finnish: Boolean): SttModelProfile {
            val family = if (finnish) SttModelFamily.MULTILINGUAL else SttModelFamily.ENGLISH_ONLY
            return entries.first { it.variant == variant && it.family == family }
        }
    }
}
