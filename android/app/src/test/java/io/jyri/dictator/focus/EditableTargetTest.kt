package io.jyri.dictator.focus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditableTargetTest {
    @Test
    fun `accepts standard editable fields without text actions`() {
        assertTrue(EditableTarget.supportsTextInput(true, false, false))
    }

    @Test
    fun `accepts custom inputs exposing either text action`() {
        assertTrue(EditableTarget.supportsTextInput(false, true, false))
        assertTrue(EditableTarget.supportsTextInput(false, false, true))
        assertTrue(EditableTarget.supportsTextInput(false, true, true))
    }

    @Test
    fun `rejects nodes without editing capabilities`() {
        assertFalse(EditableTarget.supportsTextInput(false, false, false))
    }
}
