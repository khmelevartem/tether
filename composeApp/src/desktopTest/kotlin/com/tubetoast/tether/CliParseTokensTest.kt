package com.tubetoast.tether

import kotlin.test.Test
import kotlin.test.assertEquals

class CliParseTokensTest {
    @Test
    fun `plain tokens without quotes`() {
        assertEquals(listOf("send", "peer", "/tmp/file.txt"), parseTokens("send peer /tmp/file.txt"))
    }

    @Test
    fun `double-quoted peer name with space`() {
        assertEquals(listOf("send", "Mac A", "/tmp/file.txt"), parseTokens("""send "Mac A" /tmp/file.txt"""))
    }

    @Test
    fun `double-quoted file path with spaces`() {
        assertEquals(
            listOf("send", "peer", "/Downloads/A B C.txt"),
            parseTokens("""send peer "/Downloads/A B C.txt""""),
        )
    }

    @Test
    fun `both peer and file in double quotes`() {
        assertEquals(
            listOf("send", "Mac A", "/Downloads/A B C.txt"),
            parseTokens("""send "Mac A" "/Downloads/A B C.txt""""),
        )
    }

    @Test
    fun `single-quoted peer name with space`() {
        assertEquals(listOf("send", "Mac A", "/tmp/file.txt"), parseTokens("send 'Mac A' /tmp/file.txt"))
    }

    @Test
    fun `backslash-escaped space in peer name`() {
        assertEquals(listOf("send", "Mac A", "/tmp/file.txt"), parseTokens("""send Mac\ A /tmp/file.txt"""))
    }

    @Test
    fun `backslash-escaped space in file path`() {
        assertEquals(
            listOf("send", "peer", "/Downloads/A B C.txt"),
            parseTokens("""send peer /Downloads/A\ B\ C.txt"""),
        )
    }

    @Test
    fun `empty string returns empty list`() {
        assertEquals(emptyList(), parseTokens(""))
    }

    @Test
    fun `extra whitespace between tokens`() {
        assertEquals(listOf("send", "peer", "/tmp/file.txt"), parseTokens("send   peer   /tmp/file.txt"))
    }

    // Backslash inside double quotes is an escape sequence: `\ ` → ` ` (space), same as outside quotes.
    @Test
    fun `backslash-escaped space inside double quotes`() {
        assertEquals(listOf("send", "Mac A", "/tmp/file.txt"), parseTokens("""send "Mac\ A" /tmp/file.txt"""))
    }

    // Unquoted spaces in a path produce multiple tokens — standard CLI behaviour.
    // Users must quote paths with spaces: send peer "/path/A B C.txt"
    @Test
    fun `unquoted spaces in file path split into multiple tokens`() {
        assertEquals(
            listOf("send", "peer", "/Downloads/A", "B", "C.txt"),
            parseTokens("send peer /Downloads/A B C.txt"),
        )
    }

    // Unclosed quote: inner loop exits at end of string; accumulated content is returned as a token.
    @Test
    fun `unclosed double quote yields token up to end of string`() {
        assertEquals(listOf("send", "Mac A /tmp/file.txt"), parseTokens("""send "Mac A /tmp/file.txt"""))
    }

    // Single quotes don't suppress backslash in this parser (unlike POSIX sh).
    // `\ ` inside single quotes still produces a space, not a literal backslash-space pair.
    @Test
    fun `backslash inside single quotes acts as escape`() {
        assertEquals(listOf("send", "Mac A", "/tmp/file.txt"), parseTokens("""send 'Mac\ A' /tmp/file.txt"""))
    }
}
