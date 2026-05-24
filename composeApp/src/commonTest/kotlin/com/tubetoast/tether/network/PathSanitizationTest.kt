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
}
