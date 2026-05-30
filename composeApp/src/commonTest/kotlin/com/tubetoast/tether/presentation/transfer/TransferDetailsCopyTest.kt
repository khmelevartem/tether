package com.tubetoast.tether.presentation.transfer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransferDetailsCopyTest {
    @Test
    fun aggregateStripNoFailed() {
        val result = aggregateStripCopy(sent = 5, total = 8, failed = 0)
        assertEquals("5 of 8 sent", result)
        assertFalse(result.contains("failed"))
    }

    @Test
    fun aggregateStripWithFailed() {
        val result = aggregateStripCopy(sent = 5, total = 8, failed = 3)
        assertEquals("5 of 8 sent · 3 failed", result)
    }

    @Test
    fun aggregateStripZeroFailedOmitsClause() {
        val result = aggregateStripCopy(sent = 8, total = 8, failed = 0)
        assertFalse(result.contains("failed"), "K=0 should omit the failed clause")
        assertTrue(result.contains("8 of 8"))
    }

    @Test
    fun aggregateStripOneFailed() {
        val result = aggregateStripCopy(sent = 7, total = 8, failed = 1)
        assertEquals("7 of 8 sent · 1 failed", result)
    }
}
