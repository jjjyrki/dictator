package io.jyri.dictator.insert

import org.junit.Assert.assertEquals
import org.junit.Test

class EditableNodeTextTest {
    @Test
    fun clearsTextWhenAccessibilityMarksItAsHint() {
        assertEquals("", actualText("Message", null, editable = true, showingHintText = true))
    }

    @Test
    fun clearsTextWhenEditableTextMatchesExposedHint() {
        assertEquals("", actualText("Search", "Search", editable = true, showingHintText = false))
    }

    @Test
    fun preservesUserTextThatMatchesNoHint() {
        assertEquals("Message", actualText("Message", null, editable = true, showingHintText = false))
    }

    @Test
    fun clearsAppSpecificPlaceholderWhenTheComposerHasNoSelection() {
        assertEquals(
            "",
            actualText(
                "Message",
                null,
                editable = true,
                showingHintText = false,
                textIsPlaceholder = true,
            ),
        )
    }
}
