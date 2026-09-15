package io.jyri.dictator

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DictationDiagnosticsTest {
    @Test
    fun sanitizesNewlinesBeforeWritingAnEvent() {
        assertEquals(
            "focus package=com.example target",
            sanitizeDiagnosticEvent("focus\npackage=com.example\ntarget"),
        )
    }

    @Test
    fun retainsTheNewestBytesWithinTheConfiguredLimit() {
        val retained = retainedDiagnosticBytes(
            existing = "old-event\n".toByteArray(StandardCharsets.UTF_8),
            incoming = "new-event\n".toByteArray(StandardCharsets.UTF_8),
            maxBytes = 10,
        )

        assertArrayEquals(
            "new-event\n".toByteArray(StandardCharsets.UTF_8),
            retained,
        )
    }

    @Test
    fun keepsTheTailOfAnExistingLogWhenTheNewEventFitsPartially() {
        val retained = retainedDiagnosticBytes(
            existing = "1234567890".toByteArray(StandardCharsets.UTF_8),
            incoming = "abc".toByteArray(StandardCharsets.UTF_8),
            maxBytes = 10,
        )

        assertArrayEquals("4567890abc".toByteArray(StandardCharsets.UTF_8), retained)
    }
}
