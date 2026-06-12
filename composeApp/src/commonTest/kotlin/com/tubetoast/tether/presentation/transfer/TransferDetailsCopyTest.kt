package com.tubetoast.tether.presentation.transfer

import com.tubetoast.tether.transfer.PeerTransferState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransferDetailsCopyTest {
    @Test
    fun subtitleForPreparing() {
        val state = PeerTransferState.ActiveOutbound.Preparing(
            currentFile = "photo.jpg",
            currentIndex = 1,
            totalFiles = 3,
            sentBytes = 0L,
            totalBytes = null,
            skippedCount = 0,
            perFile = emptyList(),
        )
        assertEquals("Preparing 2 of 3…", detailsSubtitleCopy(state, "peer"))
    }

    @Test
    fun subtitleForSending() {
        val state = PeerTransferState.ActiveOutbound.Sending(
            currentFile = "photo.jpg",
            currentIndex = 1,
            totalFiles = 3,
            sentBytes = 500L,
            totalBytes = 1_000L,
            bytesPerSec = 100L,
            skippedCount = 0,
            perFile = emptyList(),
        )
        assertEquals("Sending 2 of 3…", detailsSubtitleCopy(state, "peer"))
    }

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
