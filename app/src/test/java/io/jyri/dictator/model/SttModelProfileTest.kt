package io.jyri.dictator.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SttModelProfileTest {
    @Test
    fun catalogContainsEnglishAndMultilingualChoicesForEverySize() {
        assertEquals(
            setOf(SttModelVariant.TINY, SttModelVariant.BASE, SttModelVariant.SMALL),
            SttModelProfile.entries.filter { !it.isMultilingual }.map { it.variant }.toSet(),
        )
        assertEquals(
            setOf(SttModelVariant.TINY, SttModelVariant.BASE, SttModelVariant.SMALL),
            SttModelProfile.entries.filter { it.isMultilingual }.map { it.variant }.toSet(),
        )
    }

    @Test
    fun multilingualAssetsAreDistinctFromEnglishAssets() {
        assertTrue(
            SttModelProfile.MULTILINGUAL_SMALL.asset.fileName.endsWith("_acft_q8_0.bin"),
        )
        assertFalse(SttModelProfile.MULTILINGUAL_SMALL.asset.fileName.contains("_en_"))
        assertTrue(
            SttModelProfile.ENGLISH_SMALL.asset.fileName.endsWith("_en_acft_q8_0.bin"),
        )
    }
}
