package com.tubetoast.tether.transfer

import kotlin.test.Test
import kotlin.test.assertEquals

class ByteFormattingTest {
    @Test
    fun `formatSize 0 bytes`() = assertEquals("0 B", ByteFormatting.formatSize(0L))

    @Test
    fun `formatSize 1023 bytes stays in B`() = assertEquals("1023 B", ByteFormatting.formatSize(1023L))

    @Test
    fun `formatSize 1024 bytes is 1_0 KB`() = assertEquals("1.0 KB", ByteFormatting.formatSize(1024L))

    @Test
    fun `formatSize 1_500_000 bytes is 1_4 MB`() = assertEquals("1.4 MB", ByteFormatting.formatSize(1_500_000L))

    @Test
    fun `formatSize 2 GB`() = assertEquals("2.0 GB", ByteFormatting.formatSize(2L * 1024 * 1024 * 1024))

    @Test
    fun `formatSpeed null returns Calculating`() = assertEquals("Calculating…", ByteFormatting.formatSpeed(null))

    @Test
    fun `formatSpeed 1024 bytes per sec is 1_0 KB_s`() = assertEquals("1.0 KB/s", ByteFormatting.formatSpeed(1024L))
}
