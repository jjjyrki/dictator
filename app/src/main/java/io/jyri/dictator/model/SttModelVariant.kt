package io.jyri.dictator.model

/** FUTO ACFT Whisper models with pinned sizes and digests. */
enum class SttModelVariant(
    val displayName: String,
    val directoryName: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
) {
    TINY(
        displayName = "English-39 (fastest)",
        directoryName = "whisper-en-acft-tiny",
        fileName = "tiny_en_acft_q8_0.bin",
        sizeBytes = 43_550_795,
        sha256 = "4b5480aa1b14a7efc5b578ef176510970a898049671c3cd237285b3e3f6bfbfc",
    ),
    BASE(
        displayName = "English-74 (balanced)",
        directoryName = "whisper-en-acft-base",
        fileName = "base_en_acft_q8_0.bin",
        sizeBytes = 81_781_811,
        sha256 = "e9b4b7b81b8a28769e8aa9962aa39bb9f21b622cf6a63982e93f065ed5caf1c8",
    ),
    SMALL(
        displayName = "English-244 (most accurate)",
        directoryName = "whisper-en-acft-small",
        fileName = "small_en_acft_q8_0.bin",
        sizeBytes = 264_477_561,
        sha256 = "58fbe949992dafed917590d58bc12ca577b08b9957f0b3e0d7ee71b64bed3aa8",
    ),
    ;

    val downloadUrl: String
        get() = "https://voiceinput.futo.org/VoiceInput/$fileName"
}
