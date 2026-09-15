package io.jyri.dictator.speech

import io.jyri.dictator.model.SpokenLanguage
import io.jyri.dictator.model.SttModelProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class WhisperLanguageConfigTest {
    @Test
    fun multilingualModelUsesAutoDetectionForTwoSelectedLanguages() {
        val config = WhisperLanguageConfig.forModel(
            SttModelProfile.MULTILINGUAL_SMALL,
            setOf(SpokenLanguage.ENGLISH, SpokenLanguage.FINNISH),
        )

        assertEquals("auto", config.language)
        assertEquals(listOf("en", "fi"), config.allowedLanguageCodes)
    }

    @Test
    fun multilingualModelUsesAutoDetectionForFourSelectedLanguages() {
        val config = WhisperLanguageConfig.forModel(
            SttModelProfile.MULTILINGUAL_SMALL,
            setOf(
                SpokenLanguage.ENGLISH,
                SpokenLanguage.CHINESE,
                SpokenLanguage.FINNISH,
                SpokenLanguage.CANTONESE,
            ),
        )

        assertEquals("auto", config.language)
        assertEquals(listOf("en", "zh", "fi", "yue"), config.allowedLanguageCodes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun multilingualModelRejectsMoreThanFourSelectedLanguages() {
        WhisperLanguageConfig.forModel(
            SttModelProfile.MULTILINGUAL_SMALL,
            SpokenLanguage.entries.take(5).toSet(),
        )
    }

    @Test
    fun oneSelectedMultilingualLanguageUsesExplicitLanguage() {
        val config = WhisperLanguageConfig.forModel(
            SttModelProfile.MULTILINGUAL_SMALL,
            setOf(SpokenLanguage.FINNISH),
        )

        assertEquals("fi", config.language)
        assertEquals(emptyList<String>(), config.allowedLanguageCodes)
    }

    @Test
    fun englishOnlyModelAlwaysUsesEnglish() {
        val config = WhisperLanguageConfig.forModel(
            SttModelProfile.ENGLISH_SMALL,
            setOf(SpokenLanguage.ENGLISH, SpokenLanguage.FINNISH),
        )

        assertEquals("en", config.language)
        assertEquals(emptyList<String>(), config.allowedLanguageCodes)
    }
}
