package io.jyri.dictator.speech

import io.jyri.dictator.model.SpokenLanguage
import io.jyri.dictator.model.SttModelProfile

/**
 * Process-wide holder for the loaded Whisper engine. The main activity loads
 * the model once; the accessibility service reuses the same native context.
 */
object SttEngineHolder {
    @Volatile
    var engine: WhisperSttEngine? = null
        private set

    @Volatile
    var loadedModel: SttModelProfile? = null
        private set

    @Volatile
    var loadedLanguages: Set<SpokenLanguage> = emptySet()
        private set

    @Synchronized
    fun install(
        model: SttModelProfile,
        languages: Set<SpokenLanguage>,
        engine: WhisperSttEngine,
    ) {
        val previous = this.engine
        this.engine = engine
        loadedModel = model
        loadedLanguages = languages.toSet()
        if (previous !== engine) previous?.close()
    }

    fun matches(model: SttModelProfile, languages: Set<SpokenLanguage>): Boolean =
        engine != null && loadedModel == model && loadedLanguages == languages

    @Synchronized
    fun clear() {
        val previous = engine
        engine = null
        loadedModel = null
        loadedLanguages = emptySet()
        previous?.close()
    }
}
