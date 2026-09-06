package io.jyri.dictator.model

/** Size tier for FUTO ACFT Whisper models. */
enum class SttModelVariant {
    TINY,
    BASE,
    SMALL,
    ;

    fun asset(family: SttModelFamily): SttModelAsset = when (family) {
        SttModelFamily.ENGLISH_ONLY -> english
        SttModelFamily.MULTILINGUAL -> multilingual
    }

    private val english: SttModelAsset
        get() = when (this) {
            TINY -> SttModelAsset(
                displayName = "English · Tiny · 44 MB · fastest",
                directoryName = "whisper-en-acft-tiny",
                fileName = "tiny_en_acft_q8_0.bin",
                sizeBytes = 43_550_795,
                sha256 = "4b5480aa1b14a7efc5b578ef176510970a898049671c3cd237285b3e3f6bfbfc",
            )
            BASE -> SttModelAsset(
                displayName = "English · Base · 82 MB · balanced",
                directoryName = "whisper-en-acft-base",
                fileName = "base_en_acft_q8_0.bin",
                sizeBytes = 81_781_811,
                sha256 = "e9b4b7b81b8a28769e8aa9962aa39bb9f21b622cf6a63982e93f065ed5caf1c8",
            )
            SMALL -> SttModelAsset(
                displayName = "English · Small · 264 MB · best quality",
                directoryName = "whisper-en-acft-small",
                fileName = "small_en_acft_q8_0.bin",
                sizeBytes = 264_477_561,
                sha256 = "58fbe949992dafed917590d58bc12ca577b08b9957f0b3e0d7ee71b64bed3aa8",
            )
        }

    private val multilingual: SttModelAsset
        get() = when (this) {
            TINY -> SttModelAsset(
                displayName = "Multilingual · Tiny · 44 MB · fastest",
                directoryName = "whisper-acft-tiny",
                fileName = "tiny_acft_q8_0.bin",
                sizeBytes = 43_537_450,
                sha256 = "07aa4d514144deacf5ffec5cacb36c93dee272fda9e64ac33a801f8cd5cbd953",
            )
            BASE -> SttModelAsset(
                displayName = "Multilingual · Base · 82 MB · balanced",
                directoryName = "whisper-acft-base",
                fileName = "base_acft_q8_0.bin",
                sizeBytes = 81_768_602,
                sha256 = "e44f352c9aa2c3609de20c733c4ad4a75c28cd9ab07d005383df55fa96efc4",
            )
            SMALL -> SttModelAsset(
                displayName = "Multilingual · Small · 264 MB · best quality",
                directoryName = "whisper-acft-small",
                fileName = "small_acft_q8_0.bin",
                sizeBytes = 264_464_624,
                sha256 = "15ef255465a6dc582ecf1ec651a4618c7ee2c18c05570bbe46493d248d465ac4",
            )
        }
}
