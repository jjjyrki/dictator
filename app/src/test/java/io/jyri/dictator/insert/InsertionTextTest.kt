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

    @Test
    fun keepsLeadingSpaceWhenAppending() {
        val (text, cursor) = InsertionText.apply("Hello", 5, 5, " world")
        assertEquals("Hello world", text)
        assertEquals(11, cursor)
    }

    @Test
    fun dropsLeadingSpaceInEmptyField() {
        val (text, cursor) = InsertionText.apply("", 0, 0, " Hello ")
        assertEquals("Hello ", text)
        assertEquals(6, cursor)
    }

    @Test
    fun dropsLeadingSpaceWhenFieldStartsBlank() {
        val (text, cursor) = InsertionText.apply("  ", 2, 2, " Hi")
        assertEquals("  Hi", text)
        assertEquals(4, cursor)
    }

    @Test
    fun keepsLeadingSpaceWhenInsertingMidText() {
        val (text, cursor) = InsertionText.apply("Hello world", 6, 6, " big ")
        assertEquals("Hello  big world", text)
        assertEquals(11, cursor)
    }
}
