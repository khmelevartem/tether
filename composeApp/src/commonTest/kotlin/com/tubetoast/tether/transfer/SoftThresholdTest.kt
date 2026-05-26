package com.tubetoast.tether.transfer

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SoftThresholdTest {
    @Test
    fun `499 files and 0 bytes does not exceed threshold`() =
        assertFalse(SoftThreshold.exceeds(499, 0L))

    @Test
    fun `501 files and 0 bytes exceeds threshold`() =
        assertTrue(SoftThreshold.exceeds(501, 0L))

    @Test
    fun `10 files and 2 GB plus 1 byte exceeds threshold`() =
        assertTrue(SoftThreshold.exceeds(10, 2L * 1024 * 1024 * 1024 + 1))

    @Test
    fun `10 files and exactly 2 GB does not exceed threshold`() =
        assertFalse(SoftThreshold.exceeds(10, 2L * 1024 * 1024 * 1024))
}
