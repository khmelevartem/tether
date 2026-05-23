package com.tubetoast.tether.util

import kotlin.test.Test
import kotlin.test.assertEquals

class ByteFormatTest {
    @Test
    fun `0 B formats as 0 B`() = assertEquals("0 B", formatBytes(0L))

    @Test
    fun `1023 B stays in B`() = assertEquals("1023 B", formatBytes(1023L))

    @Test
    fun `1024 B formats as 1_0 KB`() = assertEquals("1.0 KB", formatBytes(1024L))

    @Test
    fun `1_047_552 B stays in KB`() = assertEquals("1023.0 KB", formatBytes(1_047_552L))

    @Test
    fun `1_048_576 B formats as 1_0 MB`() = assertEquals("1.0 MB", formatBytes(1_048_576L))

    @Test
    fun `1_073_741_824 B formats as 1_0 GB`() = assertEquals("1.0 GB", formatBytes(1_073_741_824L))

    @Test
    fun `1_572_864 B formats as 1_5 MB`() = assertEquals("1.5 MB", formatBytes(1_572_864L))
}
