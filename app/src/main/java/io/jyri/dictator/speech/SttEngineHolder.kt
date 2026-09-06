package io.jyri.dictator.speech

import io.jyri.dictator.model.SttModelVariant

/**
 * Process-wide holder for the loaded Whisper engine. The main activity loads
 * the model once; the accessibility service reuses the same native context.
 */
object SttEngineHolder {
    @Volatile
    var engine: WhisperSttEngine? = null
        private set

    @Volatile
    var loadedVariant: SttModelVariant? = null
        private set

    @Volatile
    var loadedFinnish: Boolean? = null
        private set

    @Synchronized
    fun install(variant: SttModelVariant, finnish: Boolean, engine: WhisperSttEngine) {
        val previous = this.engine
        this.engine = engine
        loadedVariant = variant
        loadedFinnish = finnish
        if (previous !== engine) previous?.close()
    }

    fun matches(variant: SttModelVariant, finnish: Boolean): Boolean =
        engine != null && loadedVariant == variant && loadedFinnish == finnish

    @Synchronized
    fun clear() {
        val previous = engine
        engine = null
        loadedVariant = null
        loadedFinnish = null
        previous?.close()
    }
}
