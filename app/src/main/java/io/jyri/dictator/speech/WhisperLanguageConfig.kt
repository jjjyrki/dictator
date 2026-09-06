package io.jyri.dictator.speech

import io.jyri.dictator.model.SpokenLanguage
import io.jyri.dictator.model.SttModelProfile

/** Language mode passed to whisper.cpp for one transcription call. */
data class WhisperLanguageConfig(
    val language: String,
    val allowedLanguageCodes: List<String> = emptyList(),
) {
    init {
        require(language.isNotBlank()) { "A Whisper language mode is required" }
        require(allowedLanguageCodes.distinct().size == allowedLanguageCodes.size) {
            "Allowed language codes must be unique"
        }
    }

    companion object {
        fun fixed(language: SpokenLanguage): WhisperLanguageConfig =
            WhisperLanguageConfig(language.whisperCode)

        fun forModel(
            model: SttModelProfile,
            languages: Set<SpokenLanguage>,
        ): WhisperLanguageConfig {
            if (!model.isMultilingual) return fixed(SpokenLanguage.ENGLISH)
            val selected = languages.ifEmpty { SpokenLanguage.entries.toSet() }
                .sortedBy { it.ordinal }
            return if (selected.size == 1) {
                fixed(selected.single())
            } else {
                WhisperLanguageConfig(
                    language = "auto",
                    allowedLanguageCodes = selected.map { it.whisperCode },
                )
            }
        }
    }
}
