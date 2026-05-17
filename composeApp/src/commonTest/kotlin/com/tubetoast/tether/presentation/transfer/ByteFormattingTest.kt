package com.tubetoast.tether.presentation.transfer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ByteFormattingTest {
    @Test
    fun bytesRange() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1 B", formatBytes(1))
        assertEquals("1023 B", formatBytes(1023))
    }

    @Test
    fun kiloBytesRange() {
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 KB", formatBytes(1536))
    }

    @Test
    fun megaBytesRange() {
        assertEquals("1.0 MB", formatBytes(1024 * 1024))
        // 12 MB + 0.5 MB in binary = 12.5 MB
        assertEquals("12.5 MB", formatBytes(1024 * 1024 * 12 + 1024 * 512))
    }

    @Test
    fun gigaBytesRange() {
        assertEquals("1.0 GB", formatBytes(1024 * 1024 * 1024))
    }

    @Test
    fun speedSuffix() {
        val speed = formatSpeed(2_100_000L)
        assertTrue(speed.endsWith("/s"), "Expected speed to end with /s, got: $speed")
        assertTrue(speed.contains("MB"), "Expected MB in speed, got: $speed")
    }

    @Test
    fun progressWithTotal() {
        val text = formatProgress(12_800_000L, 48_700_000L, 2_100_000L)
        assertTrue(text.contains("of"), "Expected 'of' in progress text")
        assertTrue(text.contains("·"), "Expected separator in progress text")
    }

    @Test
    fun progressWithoutTotal() {
        val text = formatProgress(5_000_000L, null, 1_000_000L)
        assertTrue(text.contains("·"), "Expected separator in progress text")
        assertTrue(!text.contains("of"), "Should not contain 'of' without total")
    }
}
