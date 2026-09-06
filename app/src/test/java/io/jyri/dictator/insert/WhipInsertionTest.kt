package io.jyri.dictator.insert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhipInsertionTest {
    @Test
    fun prefersImeOverPaste() {
        var pasted = false
        val outcome = WhipInsertion.insert(
            transcript = "hello",
            replaceAll = false,
            typeIntoEditor = { _, _ -> true },
            paste = {
                pasted = true
                true
            },
            copy = { false },
        )
        assertEquals(InsertionOutcome.Direct, outcome)
        assertFalse(pasted)
    }

    @Test
    fun fallsBackToPasteWhenImeUnavailable() {
        var pasted = false
        val outcome = WhipInsertion.insert(
            transcript = "hello",
            replaceAll = false,
            typeIntoEditor = { _, _ -> false },
            paste = {
                pasted = true
                true
            },
            copy = { false },
        )
        assertEquals(InsertionOutcome.Direct, outcome)
        assertTrue(pasted)
    }

    @Test
    fun copiesWhenImeAndPasteFail() {
        val outcome = WhipInsertion.insert(
            transcript = "hello",
            replaceAll = true,
            typeIntoEditor = { _, _ -> false },
            paste = { false },
            copy = { true },
        )
        assertEquals(InsertionOutcome.ClipboardFallback, outcome)
    }

    @Test
    fun convertsNewlinesForTerminalInput() {
        assertEquals("a\rb\rc", WhipInsertion.prepareTypedText("a\nb\r\nc"))
    }

    @Test
    fun typesCarriageReturnsInsteadOfNewlines() {
        var typed: String? = null
        val outcome = WhipInsertion.insert(
            transcript = "a\nb",
            replaceAll = false,
            typeIntoEditor = { text, _ ->
                typed = text
                true
            },
            paste = { false },
            copy = { false },
        )
        assertEquals(InsertionOutcome.Direct, outcome)
        assertEquals("a\rb", typed)
    }

    @Test
    fun neverReplacesTheWholeTerminal() {
        var receivedReplace = true
        val outcome = WhipInsertion.insert(
            transcript = "hello",
            replaceAll = true,
            typeIntoEditor = { _, replace ->
                receivedReplace = replace
                true
            },
            paste = { false },
            copy = { false },
        )
        assertEquals(InsertionOutcome.Direct, outcome)
        assertFalse(receivedReplace)
    }

    @Test
    fun failsWhenEveryPathFails() {
        val outcome = WhipInsertion.insert(
            transcript = "hello",
            replaceAll = false,
            typeIntoEditor = { _, _ -> false },
            paste = { false },
            copy = { false },
        )
        assertEquals(InsertionOutcome.Failed, outcome)
    }
}
