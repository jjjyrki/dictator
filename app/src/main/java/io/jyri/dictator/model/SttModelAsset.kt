package io.jyri.dictator.model

/** One downloadable FUTO ACFT Whisper GGML file plus the language it should run as. */
data class SttModelAsset(
    val displayName: String,
    val directoryName: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val language: String,
) {
    val downloadUrl: String
        get() = "https://voiceinput.futo.org/VoiceInput/$fileName"
}
