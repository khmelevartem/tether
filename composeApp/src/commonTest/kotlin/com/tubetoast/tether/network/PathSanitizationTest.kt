package com.tubetoast.tether.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PathSanitizationTest {
    private fun sanitize(raw: String) = PathSanitization.sanitizeRelativePath(raw)

    @Test
    fun `empty input rejects`() {
        assertNull(sanitize(""))
    }

    @Test
    fun `simple filename passes`() {
        assertEquals("photo.jpg", sanitize("photo.jpg"))
    }

    @Test
    fun `nested path passes and is preserved`() {
        assertEquals("Vacation/2024/IMG_001.jpg", sanitize("Vacation/2024/IMG_001.jpg"))
    }

    @Test
    fun `dotdot segment rejects`() {
        assertNull(sanitize("../escape.txt"))
        assertNull(sanitize("a/../escape.txt"))
        assertNull(sanitize("a/b/../../escape.txt"))
    }

    @Test
    fun `single dot segment rejects`() {
        assertNull(sanitize("."))
        assertNull(sanitize("./file.txt"))
        assertNull(sanitize("a/./b.txt"))
    }

    @Test
    fun `triple dot is a literal name and allowed`() {
        assertNotNull(sanitize("..."))
        assertNotNull(sanitize("a/.../b.txt"))
        assertNotNull(sanitize("...."))
    }

    @Test
    fun `absolute path with leading slash rejects`() {
        assertNull(sanitize("/etc/passwd"))
        assertNull(sanitize("/foo/bar.txt"))
    }

    @Test
    fun `drive letter prefix rejects`() {
        assertNull(sanitize("C:foo.txt"))
        assertNull(sanitize("C:/foo.txt"))
        assertNull(sanitize("C:\\foo.txt"))
        assertNull(sanitize("z:bar"))
    }

    @Test
    fun `url-encoded dotdot rejects`() {
        assertNull(sanitize("%2e%2e/escape.txt"))
        assertNull(sanitize("..%2fescape.txt"))
        assertNull(sanitize("%2e%2e%2fescape.txt"))
    }

    @Test
    fun `url-encoded slash normalised to separator and nested path allowed`() {
        assertEquals("a/b/c", sanitize("a%2fb%2fc"))
    }

    @Test
    fun `backslash normalised to forward slash`() {
        assertEquals("a/b/c.txt", sanitize("a\\b\\c.txt"))
    }

    @Test
    fun `runs of slashes produce empty segments and reject`() {
        assertNull(sanitize("a//b"))
        assertNull(sanitize("a///b"))
    }

    @Test
    fun `trailing slash rejects`() {
        assertNull(sanitize("a/b/"))
    }

    @Test
    fun `leading backslash rejects`() {
        assertNull(sanitize("\\evil.txt"))
    }

    @Test
    fun `unicode and emoji in names allowed`() {
        assertNotNull(sanitize("Отпуск/фото.jpg"))
        assertNotNull(sanitize("vacation/🏖️/photo.jpg"))
    }

    @Test
    fun `null byte in input rejects`() {
        assertNull(sanitize("foo\u0000bar.txt"))
        assertNull(sanitize("foo%00bar.txt"))
    }

    @Test
    fun `url-encoded uppercase hex passes decode`() {
        assertEquals("foo.txt", sanitize("foo%2Etxt"))
    }

    @Test
    fun `malformed UTF-8 percent-encoded bytes reject`() {
        // lone continuation byte (0x80 has no preceding start byte)
        assertNull(sanitize("%80"))
        // truncated 2-byte sequence (C3 expects a continuation, not literal char)
        assertNull(sanitize("%C3and-then-ascii"))
        // overlong encoding of '.' (%C0%AE) — must not slip through as literal dot
        assertNull(sanitize("%C0%AE"))
        // overlong encoding of NUL (%C0%80) — must not bypass the NUL-byte guard
        assertNull(sanitize("foo%C0%80bar.txt"))
    }

    @Test
    fun `url-encoded UTF-8 multi-byte decodes to original codepoints`() {
        // %D0%9E%D1%82%D0%BF%D1%83%D1%81%D0%BA → "Отпуск"
        assertEquals("Отпуск", sanitize("%D0%9E%D1%82%D0%BF%D1%83%D1%81%D0%BA"))
        // %C3%A9 → "é" (single Latin-1 supplement codepoint, two UTF-8 bytes)
        assertEquals("résumé.txt", sanitize("r%C3%A9sum%C3%A9.txt"))
        // %F0%9F%8F%96 → 🏖 (four-byte UTF-8 emoji)
        assertEquals("vacation/🏖.jpg", sanitize("vacation/%F0%9F%8F%96.jpg"))
    }

    @Test
    fun `percent-encoded space decodes to space`() {
        assertEquals("manual with space.txt", sanitize("manual%20with%20space.txt"))
    }

    @Test
    fun `percent-encoded hash decodes correctly`() {
        assertEquals("file#name.txt", sanitize("file%23name.txt"))
    }

    @Test
    fun `percent-encoded question mark decodes correctly`() {
        assertEquals("file?name.txt", sanitize("file%3Fname.txt"))
    }

    @Test
    fun `percent-encoded ampersand decodes correctly`() {
        assertEquals("key&value.txt", sanitize("key%26value.txt"))
    }

    @Test
    fun `percent-encoded equals decodes correctly`() {
        assertEquals("key=value.txt", sanitize("key%3Dvalue.txt"))
    }

    @Test
    fun `percent-encoded percent decodes correctly`() {
        assertEquals("50%.txt", sanitize("50%25.txt"))
    }

    @Test
    fun `plus literal in filename is preserved as-is`() {
        assertEquals("a+b.txt", sanitize("a+b.txt"))
    }

    @Test
    fun `mixed Latin and Cyrillic filename passes`() {
        assertEquals("report_Отчёт.pdf", sanitize("report_%D0%9E%D1%82%D1%87%D1%91%D1%82.pdf"))
    }
}
