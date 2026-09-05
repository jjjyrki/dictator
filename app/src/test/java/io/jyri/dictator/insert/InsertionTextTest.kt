package io.jyri.dictator.insert

import org.junit.Assert.assertEquals
import org.junit.Test

class InsertionTextTest {
    @Test
    fun insertsAtCursor() {
        val (text, cursor) = InsertionText.apply("Hello world", 6, 6, "there ")
        assertEquals("Hello there world", text)
        assertEquals(12, cursor)
    }

    @Test
    fun replacesSelection() {
        val (text, cursor) = InsertionText.apply("Hello world", 6, 11, "Jyri")
        assertEquals("Hello Jyri", text)
        assertEquals(10, cursor)
    }

    @Test
    fun clampsInvertedSelection() {
        val (text, cursor) = InsertionText.apply("abc", 2, 1, "X")
        assertEquals("abXc", text)
        assertEquals(3, cursor)
    }

    @Test
    fun clampsOutOfRange() {
        val (text, cursor) = InsertionText.apply("ab", 40, 40, "c")
        assertEquals("abc", text)
        assertEquals(3, cursor)
    }
}
