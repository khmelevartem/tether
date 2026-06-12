package com.tubetoast.tether.presentation.peercard

import com.tubetoast.tether.transfer.PeerTransferState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PeerCardActiveProgressTest {
    @Test
    fun outbound_claimed_withKnownTotal_isDeterminate() {
        val state = PeerTransferState.ActiveOutbound.Claimed(
            totalFiles = 3,
            totalBytes = 30_000L,
            perFile = emptyList(),
        )
        val result = outboundCardProgress(state)
        assertFalse(result.indeterminate)
        assertEquals(0f, result.progress)
    }

    @Test
    fun outbound_claimed_nullTotal_isIndeterminate() {
        val state = PeerTransferState.ActiveOutbound.Claimed(
            totalFiles = 3,
            totalBytes = null,
            perFile = emptyList(),
        )
        val result = outboundCardProgress(state)
        assertTrue(result.indeterminate)
        assertEquals(0f, result.progress)
    }

    @Test
    fun outbound_claimed_zeroTotal_isIndeterminate() {
        val state = PeerTransferState.ActiveOutbound.Claimed(
            totalFiles = 1,
            totalBytes = 0L,
            perFile = emptyList(),
        )
        val result = outboundCardProgress(state)
        assertTrue(result.indeterminate)
        assertEquals(0f, result.progress)
    }

    @Test
    fun outbound_sending_preparing_isIndeterminate() {
        val result = outboundCardProgress(sending(sentBytes = 0L, totalBytes = 10_000L, preparing = true))
        assertTrue(result.indeterminate)
        assertEquals(0f, result.progress)
    }

    @Test
    fun outbound_sending_nullTotal_isIndeterminate() {
        val result = outboundCardProgress(sending(sentBytes = 5_000L, totalBytes = null))
        assertTrue(result.indeterminate)
        assertEquals(0f, result.progress)
    }

    @Test
    fun outbound_sending_zeroTotal_isIndeterminate() {
        val result = outboundCardProgress(sending(sentBytes = 0L, totalBytes = 0L))
        assertTrue(result.indeterminate)
        assertEquals(0f, result.progress)
    }

    @Test
    fun outbound_sending_zeroPercent() {
        val result = outboundCardProgress(sending(sentBytes = 0L, totalBytes = 1_000L))
        assertFalse(result.indeterminate)
        assertEquals(0f, result.progress)
    }

    @Test
    fun outbound_sending_fiftyPercent() {
        val result = outboundCardProgress(sending(sentBytes = 500L, totalBytes = 1_000L))
        assertFalse(result.indeterminate)
        assertEquals(0.5f, result.progress)
    }

    @Test
    fun outbound_sending_hundredPercent() {
        val result = outboundCardProgress(sending(sentBytes = 1_000L, totalBytes = 1_000L))
        assertFalse(result.indeterminate)
        assertEquals(1f, result.progress)
    }

    @Test
    fun outbound_sending_clampedAboveOne() {
        val result = outboundCardProgress(sending(sentBytes = 1_200L, totalBytes = 1_000L))
        assertFalse(result.indeterminate)
        assertEquals(1f, result.progress)
    }

    @Test
    fun inbound_nullTotal_isIndeterminate() {
        val result = inboundCardProgress(receiving(receivedBytes = 500L, totalBytes = null))
        assertTrue(result.indeterminate)
        assertEquals(0f, result.progress)
    }

    @Test
    fun inbound_zeroTotal_isIndeterminate() {
        val result = inboundCardProgress(receiving(receivedBytes = 0L, totalBytes = 0L))
        assertTrue(result.indeterminate)
        assertEquals(0f, result.progress)
    }

    @Test
    fun inbound_zeroPercent() {
        val result = inboundCardProgress(receiving(receivedBytes = 0L, totalBytes = 1_000L))
        assertFalse(result.indeterminate)
        assertEquals(0f, result.progress)
    }

    @Test
    fun inbound_fiftyPercent() {
        val result = inboundCardProgress(receiving(receivedBytes = 500L, totalBytes = 1_000L))
        assertFalse(result.indeterminate)
        assertEquals(0.5f, result.progress)
    }

    @Test
    fun inbound_hundredPercent() {
        val result = inboundCardProgress(receiving(receivedBytes = 1_000L, totalBytes = 1_000L))
        assertFalse(result.indeterminate)
        assertEquals(1f, result.progress)
    }

    @Test
    fun inbound_clampedAboveOne() {
        val result = inboundCardProgress(receiving(receivedBytes = 1_100L, totalBytes = 1_000L))
        assertFalse(result.indeterminate)
        assertEquals(1f, result.progress)
    }

    private fun sending(
        sentBytes: Long,
        totalBytes: Long?,
        preparing: Boolean = false,
    ) = PeerTransferState.ActiveOutbound.Sending(
        currentFile = "a.jpg",
        currentIndex = 0,
        totalFiles = 1,
        sentBytes = sentBytes,
        totalBytes = totalBytes,
        bytesPerSec = null,
        skippedCount = 0,
        preparing = preparing,
        perFile = emptyList(),
    )

    private fun receiving(
        receivedBytes: Long,
        totalBytes: Long?,
    ) = PeerTransferState.ActiveInbound(
        currentFile = "b.jpg",
        currentIndex = 0,
        totalFiles = 1,
        receivedBytes = receivedBytes,
        totalBytes = totalBytes,
        bytesPerSec = null,
        perFile = emptyList(),
    )
}
