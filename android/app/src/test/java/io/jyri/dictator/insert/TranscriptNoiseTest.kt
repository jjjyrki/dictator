package io.jyri.dictator.insert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranscriptNoiseTest {
    @Test
    fun discardsBlankAudioTag() {
        assertNull(TranscriptNoise.usableSpeech("[BLANK_AUDIO]"))
        assertNull(TranscriptNoise.usableSpeech("  [BLANK_AUDIO]  "))
        assertNull(TranscriptNoise.usableSpeech("(blank audio)"))
    }

    @Test
    fun discardsMusicAndStackedTags() {
        assertNull(TranscriptNoise.usableSpeech("[MUSIC]"))
        assertNull(TranscriptNoise.usableSpeech("[BLANK_AUDIO] [MUSIC]"))
        assertNull(TranscriptNoise.usableSpeech("♪"))
    }

    @Test
    fun discardsTypingTag() {
        assertNull(TranscriptNoise.usableSpeech("(typing)"))
        assertNull(TranscriptNoise.usableSpeech("[TYPING]"))
    }

    @Test
    fun keepsRealSpeech() {
        assertEquals("Hello there.", TranscriptNoise.usableSpeech(" Hello there. "))
    }

    @Test
    fun stripsNoiseTagFromSpeech() {
        assertEquals("Hello", TranscriptNoise.usableSpeech("Hello [BLANK_AUDIO]"))
        assertEquals("Hello there", TranscriptNoise.usableSpeech("[MUSIC] Hello there"))
    }

    @Test
    fun discardsSilenceAndEmpty() {
        assertNull(TranscriptNoise.usableSpeech(""))
        assertNull(TranscriptNoise.usableSpeech("   "))
        assertNull(TranscriptNoise.usableSpeech("[SILENCE]"))
        assertNull(TranscriptNoise.usableSpeech("[INAUDIBLE]"))
        assertNull(TranscriptNoise.usableSpeech("[]"))
        assertNull(TranscriptNoise.usableSpeech("..."))
    }
}
