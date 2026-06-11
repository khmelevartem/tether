package com.tubetoast.tether.presentation.peercard

import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.Direction
import com.tubetoast.tether.transfer.PartialOutcome
import com.tubetoast.tether.transfer.PeerTransferState
import com.tubetoast.tether.transfer.TransferErrorReason
import kotlin.test.Test
import kotlin.test.assertEquals

class PeerCardCopyTest {
    private val device = Device(name = "Alice", host = "h", port = 1, fingerprint = "fp-abc")

    @Test
    fun sentFullCopy() {
        val state = PeerTransferState.Sent(sent = 5, total = 5, perFile = emptyList(), partialReason = null)
        assertEquals("Sent 5 files to Alice", sentCardCopy(state, device))
    }

    @Test
    fun sentCardCopyUsesDeviceNameNotFingerprint() {
        // device.name ("Alice") differs from its fingerprint ("fp-abc"); the copy reads name from the device.
        val state = PeerTransferState.Sent(sent = 3, total = 3, perFile = emptyList(), partialReason = null)
        assertEquals("Sent 3 files to Alice", sentCardCopy(state, device))
    }

    @Test
    fun sentSenderCancelledCopy() {
        val state = PeerTransferState.Sent(
            sent = 3,
            total = 5,
            perFile = emptyList(),
            partialReason = PartialOutcome.SenderCancelled,
        )
        assertEquals("Sent 3 of 5 files to Alice (transfer was cancelled)", sentCardCopy(state, device))
    }

    @Test
    fun sentReceiverCancelledCopy() {
        val state = PeerTransferState.Sent(
            sent = 3,
            total = 5,
            perFile = emptyList(),
            partialReason = PartialOutcome.ReceiverCancelled,
        )
        assertEquals("Sent 3 of 5 files to Alice (transfer was cancelled)", sentCardCopy(state, device))
    }

    @Test
    fun sentConnectionLostCopy() {
        val state = PeerTransferState.Sent(
            sent = 2,
            total = 5,
            perFile = emptyList(),
            partialReason = PartialOutcome.ConnectionLost,
        )
        assertEquals("Sent 2 of 5 files to Alice (connection lost)", sentCardCopy(state, device))
    }

    @Test
    fun sentFilesUnreadableCopy() {
        val state = PeerTransferState.Sent(
            sent = 3,
            total = 5,
            perFile = emptyList(),
            partialReason = PartialOutcome.FilesUnreadable(2),
        )
        assertEquals("Sent 3 of 5 files to Alice (2 files couldn't be read)", sentCardCopy(state, device))
    }

    @Test
    fun receivedFullCopy() {
        val state = PeerTransferState.Received(
            received = 5,
            total = 5,
            perFile = emptyList(),
            partialReason = null,
        )
        assertEquals("Received 5 files from Alice — tap to open", receivedCardCopy(state, device))
    }

    @Test
    fun receivedSenderCancelledCopy() {
        val state = PeerTransferState.Received(
            received = 3,
            total = 5,
            perFile = emptyList(),
            partialReason = PartialOutcome.SenderCancelled,
        )
        assertEquals("Received 3 files from Alice — sender cancelled.", receivedCardCopy(state, device))
    }

    @Test
    fun receivedReceiverCancelledCopy() {
        val state = PeerTransferState.Received(
            received = 3,
            total = 5,
            perFile = emptyList(),
            partialReason = PartialOutcome.ReceiverCancelled,
        )
        assertEquals("Received 3 files from Alice — you cancelled.", receivedCardCopy(state, device))
    }

    @Test
    fun receivedConnectionLostCopy() {
        val state = PeerTransferState.Received(
            received = 3,
            total = 5,
            perFile = emptyList(),
            partialReason = PartialOutcome.ConnectionLost,
        )
        assertEquals("Received 3 files from Alice — connection lost.", receivedCardCopy(state, device))
    }

    @Test
    fun receiverCancelledIsLockedCopy() {
        val state = PeerTransferState.Received(
            received = 2,
            total = 5,
            perFile = emptyList(),
            partialReason = PartialOutcome.ReceiverCancelled,
        )
        assertEquals("Received 2 files from Alice — you cancelled.", receivedCardCopy(state, device))
    }

    @Test
    fun errorNetworkLostCopy() {
        val state = PeerTransferState.Error(
            reason = TransferErrorReason.NetworkLost,
            sent = 0,
            perFile = emptyList(),
        )
        assertEquals("Connection lost. Try again when you're back on Wi-Fi.", errorCardCopy(state, device))
    }

    @Test
    fun errorPeerUnreachableCopy() {
        val state = PeerTransferState.Error(
            reason = TransferErrorReason.PeerUnreachable,
            sent = 0,
            perFile = emptyList(),
        )
        assertEquals("Alice is no longer reachable. Try again.", errorCardCopy(state, device))
    }

    @Test
    fun errorReceiverWriteFailedCopy() {
        val state = PeerTransferState.Error(
            reason = TransferErrorReason.ReceiverWriteFailed,
            sent = 0,
            perFile = emptyList(),
        )
        assertEquals("Couldn't save on Alice. Free up space and try again.", errorCardCopy(state, device))
    }

    @Test
    fun errorAllFilesFailedCopy() {
        val state = PeerTransferState.Error(
            reason = TransferErrorReason.AllFilesFailed,
            sent = 0,
            perFile = emptyList(),
        )
        assertEquals("Couldn't send to Alice. Try again.", errorCardCopy(state, device))
    }

    @Test
    fun errorReceiverSuspendedCopy() {
        val state = PeerTransferState.Error(
            reason = TransferErrorReason.ReceiverSuspended,
            sent = 0,
            perFile = emptyList(),
        )
        assertEquals("Transfer from Alice was interrupted. Ask Alice to send again.", errorCardCopy(state, device))
    }

    @Test
    fun cancelledCleanCopy() {
        val state = PeerTransferState.Cancelled(sent = 0, remaining = emptyList(), perFile = emptyList())
        assertEquals("Cancelled", cancelledCardCopy(state))
    }

    @Test
    fun cancelledPartialCopy() {
        val state = PeerTransferState.Cancelled(
            sent = 3,
            remaining = listOf("a.txt", "b.txt"),
            perFile = emptyList(),
        )
        assertEquals("Cancelled. 3 files were received before cancel. 2 were not sent.", cancelledCardCopy(state))
    }

    @Test
    fun reconnectingCopy() {
        val state = PeerTransferState.Reconnecting(
            direction = Direction.Outbound,
            remainingSeconds = 12,
            snapshotBeforeDrop = PeerTransferState.Idle,
        )
        assertEquals("Reconnecting to Alice… (12s)", reconnectingCardCopy(state, device))
    }
}
