package com.tubetoast.tether.presentation.peercard

import com.tubetoast.tether.transfer.PeerTransferState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PeerCardActiveProgressTest {
    // --- outboundCardProgress ---

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
        val state = PeerTransferState.ActiveOutbound.Sending(
            currentFile = "a.heic",
            currentIndex = 0,
            totalFiles = 2,
            sentBytes = 0L,
            totalBytes = 10_000L,
            bytesPerSec = null,
            skippedCount = 0,
            preparing = true,
            perFile = emptyList(),
        )
        val result = outboundCardProgress(state)
        assertTrue(result.indeterminate)
        assertEquals(0f, result.progress)
    }

    @Test
    fun outbound_sending_nullTotal_isIndeterminate() {
        val state = PeerTransferState.ActiveOutbound.Sending(
            currentFile = "a.jpg",
            currentIndex = 0,
            totalFiles = 3,
            sentBytes = 5_000L,
            totalBytes = null,
            bytesPerSec = null,
            skippedCount = 0,
            perFile = emptyList(),
        )
        val result = outboundCardProgress(state)
        assertTrue(result.indeterminate)
        assertEquals(0f, result.progress)
    }

    @Test
    fun outbound_sending_zeroTotal_isIndeterminate() {
        val state = PeerTransferState.ActiveOutbound.Sending(
            currentFile = "a.jpg",
            currentIndex = 0,
            totalFiles = 1,
            sentBytes = 0L,
            totalBytes = 0L,
            bytesPerSec = null,
            skippedCount = 0,
            perFile = emptyList(),
        )
        val result = outboundCardProgress(state)
        assertTrue(result.indeterminate)
        assertEquals(0f, result.progress)
    }

    @Test
    fun outbound_sending_zeroPercent() {
        val state = PeerTransferState.ActiveOutbound.Sending(
            currentFile = "a.jpg",
            currentIndex = 0,
            totalFiles = 1,
            sentBytes = 0L,
            totalBytes = 1_000L,
            bytesPerSec = null,
            skippedCount = 0,
            perFile = emptyList(),
        )
        val result = outboundCardProgress(state)
        assertFalse(result.indeterminate)
        assertEquals(0f, result.progress)
    }

    @Test
    fun outbound_sending_fiftyPercent() {
        val state = PeerTransferState.ActiveOutbound.Sending(
            currentFile = "a.jpg",
            currentIndex = 0,
            totalFiles = 1,
            sentBytes = 500L,
            totalBytes = 1_000L,
            bytesPerSec = null,
            skippedCount = 0,
            perFile = emptyList(),
        )
        val result = outboundCardProgress(state)
        assertFalse(result.indeterminate)
        assertEquals(0.5f, result.progress)
    }

    @Test
    fun outbound_sending_hundredPercent() {
        val state = PeerTransferState.ActiveOutbound.Sending(
            currentFile = "a.jpg",
            currentIndex = 0,
            totalFiles = 1,
            sentBytes = 1_000L,
            totalBytes = 1_000L,
            bytesPerSec = null,
            skippedCount = 0,
            perFile = emptyList(),
        )
        val result = outboundCardProgress(state)
        assertFalse(result.indeterminate)
        assertEquals(1f, result.progress)
    }

    @Test
    fun outbound_sending_clampedAboveOne() {
        val state = PeerTransferState.ActiveOutbound.Sending(
            currentFile = "a.jpg",
            currentIndex = 0,
            totalFiles = 1,
            sentBytes = 1_200L,
            totalBytes = 1_000L,
            bytesPerSec = null,
            skippedCount = 0,
            perFile = emptyList(),
        )
        val result = outboundCardProgress(state)
        assertFalse(result.indeterminate)
        assertEquals(1f, result.progress)
    }

    // --- inboundCardProgress ---

    @Test
    fun inbound_nullTotal_isIndeterminate() {
        val state = PeerTransferState.ActiveInbound(
            currentFile = "b.jpg",
            currentIndex = 0,
            totalFiles = 2,
            receivedBytes = 500L,
            totalBytes = null,
            bytesPerSec = null,
            perFile = emptyList(),
        )
        val result = inboundCardProgress(state)
        assertTrue(result.indeterminate)
        assertEquals(0f, result.progress)
    }

    @Test
    fun inbound_zeroTotal_isIndeterminate() {
        val state = PeerTransferState.ActiveInbound(
            currentFile = "b.jpg",
            currentIndex = 0,
            totalFiles = 1,
            receivedBytes = 0L,
            totalBytes = 0L,
            bytesPerSec = null,
            perFile = emptyList(),
        )
        val result = inboundCardProgress(state)
        assertTrue(result.indeterminate)
        assertEquals(0f, result.progress)
    }

    @Test
    fun inbound_fiftyPercent() {
        val state = PeerTransferState.ActiveInbound(
            currentFile = "b.jpg",
            currentIndex = 0,
            totalFiles = 1,
            receivedBytes = 500L,
            totalBytes = 1_000L,
            bytesPerSec = null,
            perFile = emptyList(),
        )
        val result = inboundCardProgress(state)
        assertFalse(result.indeterminate)
        assertEquals(0.5f, result.progress)
    }

    @Test
    fun inbound_hundredPercent() {
        val state = PeerTransferState.ActiveInbound(
            currentFile = "b.jpg",
            currentIndex = 0,
            totalFiles = 1,
            receivedBytes = 1_000L,
            totalBytes = 1_000L,
            bytesPerSec = null,
            perFile = emptyList(),
        )
        val result = inboundCardProgress(state)
        assertFalse(result.indeterminate)
        assertEquals(1f, result.progress)
    }

    @Test
    fun inbound_clampedAboveOne() {
        val state = PeerTransferState.ActiveInbound(
            currentFile = "b.jpg",
            currentIndex = 0,
            totalFiles = 1,
            receivedBytes = 1_100L,
            totalBytes = 1_000L,
            bytesPerSec = null,
            perFile = emptyList(),
        )
        val result = inboundCardProgress(state)
        assertFalse(result.indeterminate)
        assertEquals(1f, result.progress)
    }
}
