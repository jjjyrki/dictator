package io.jyri.dictator.model

/** One downloadable FUTO ACFT Whisper GGML file. */
data class SttModelAsset(
    val languageName: String,
    val modelName: String,
    val sizeLabel: String,
    val usageDescription: String,
    val directoryName: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
) {
    val displayName: String
        get() = "$languageName · $modelName · $sizeLabel · $usageDescription"

    val downloadUrl: String
        get() = "https://voiceinput.futo.org/VoiceInput/$fileName"
}
