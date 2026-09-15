package io.jyri.dictator.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class StubSpeechEngineTest {
    @Test
    fun finishReturnsCannedSentence() {
        val session = StubSpeechEngine().start()
        assertEquals(StubSpeechEngine.CANNED_TRANSCRIPT, session.finish())
    }

    @Test
    fun cancelPreventsInsertText() {
        val session = StubSpeechEngine().start()
        session.cancel()
        assertEquals("", session.finish())
    }
}
