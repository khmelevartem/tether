package com.tubetoast.tether.network

import io.ktor.http.encodeURLQueryComponent
import kotlin.test.Test
import kotlin.test.assertEquals

class FilenameEncodingRoundTripTest {
    private fun roundTrip(filename: String): String? =
        PathSanitization.sanitizeRelativePath(filename.encodeURLQueryComponent(encodeFull = true))

    @Test
    fun `space survives round-trip`() {
        assertEquals("manual with space.txt", roundTrip("manual with space.txt"))
    }

    @Test
    fun `hash survives round-trip`() {
        assertEquals("file#name.txt", roundTrip("file#name.txt"))
    }

    @Test
    fun `question mark survives round-trip`() {
        assertEquals("file?name.txt", roundTrip("file?name.txt"))
    }

    @Test
    fun `ampersand survives round-trip`() {
        assertEquals("a&b.txt", roundTrip("a&b.txt"))
    }

    @Test
    fun `equals sign survives round-trip`() {
        assertEquals("k=v.txt", roundTrip("k=v.txt"))
    }

    @Test
    fun `percent sign survives round-trip`() {
        assertEquals("50%.txt", roundTrip("50%.txt"))
    }

    @Test
    fun `mixed Latin and Cyrillic name survives round-trip`() {
        assertEquals("report_Отчёт.pdf", roundTrip("report_Отчёт.pdf"))
    }

    @Test
    fun `nested path with spaces survives round-trip`() {
        assertEquals("My Folder/my file.txt", roundTrip("My Folder/my file.txt"))
    }
}
