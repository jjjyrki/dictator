package io.jyri.dictator.model

/** Languages that the multilingual Whisper model may consider during detection. */
enum class SpokenLanguage(val whisperCode: String) {
    ENGLISH("en"),
    FINNISH("fi"),
    ;

    companion object {
        fun fromWhisperCode(code: String): SpokenLanguage? =
            entries.firstOrNull { it.whisperCode == code }
    }
}
