package com.tubetoast.tether.ui.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

class MiddleEllipsizeTest {
    private fun fitsUpTo(maxWidth: Int): (String) -> Boolean = { candidate ->
        candidate.length <= maxWidth
    }

    @Test
    fun `empty string returns ellipsis`() {
        val result = middleEllipsize("", availableWidth = 10, fits = fitsUpTo(10))
        assertEquals("…", result)
    }

    @Test
    fun `short string that fits is returned unchanged`() {
        val result = middleEllipsize("hello", availableWidth = 10, fits = fitsUpTo(10))
        assertEquals("hello", result)
    }

    @Test
    fun `long string that never fits returns bare ellipsis`() {
        val result = middleEllipsize("abcdefghij", availableWidth = 0, fits = { false })
        assertEquals("…", result)
    }

    @Test
    fun `exact boundary returns full string`() {
        val text = "abcde"
        val result = middleEllipsize(text, availableWidth = text.length, fits = fitsUpTo(text.length))
        assertEquals(text, result)
    }

    @Test
    fun `symmetric truncation preserves head and tail`() {
        val result = middleEllipsize("abcdefghij", availableWidth = 5, fits = fitsUpTo(5))
        assertEquals("ab…ij", result)
    }

    @Test
    fun `max width of Int_MAX_VALUE returns text unchanged`() {
        val text = "some-file-name.pdf"
        val result = middleEllipsize(text, availableWidth = Int.MAX_VALUE, fits = { false })
        assertEquals(text, result)
    }
}
