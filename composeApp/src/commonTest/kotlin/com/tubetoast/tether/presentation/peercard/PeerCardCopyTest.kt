package com.tubetoast.tether.presentation.peercard

import com.tubetoast.tether.presentation.transfer.PeerTransferState
import com.tubetoast.tether.transfer.PartialOutcome
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.transfer.TransferErrorReason
import kotlin.test.Test
import kotlin.test.assertEquals

class PeerCardCopyTest {
    private val peer = PeerIdentity("Alice")

    @Test
    fun sentFullCopy() {
        val state = PeerTransferState.Sent(peer, sent = 5, total = 5, perFile = emptyList(), partialReason = null)
        assertEquals("Sent 5 files to Alice", sentCardCopy(state))
    }

    @Test
    fun sentReceiverCancelledCopy() {
        val state = PeerTransferState.Sent(
            peer,
            sent = 3,
            total = 5,
            perFile = emptyList(),
            partialReason = PartialOutcome.ReceiverCancelled,
        )
        assertEquals("Sent 3 of 5 files to Alice (transfer was cancelled)", sentCardCopy(state))
    }

    @Test
    fun sentConnectionLostCopy() {
        val state = PeerTransferState.Sent(
            peer,
            sent = 2,
            total = 5,
            perFile = emptyList(),
            partialReason = PartialOutcome.ConnectionLost,
        )
        assertEquals("Sent 2 of 5 files to Alice (connection lost)", sentCardCopy(state))
    }

    @Test
    fun sentFilesUnreadableCopy() {
        val state = PeerTransferState.Sent(
            peer,
            sent = 3,
            total = 5,
            perFile = emptyList(),
            partialReason = PartialOutcome.FilesUnreadable(2),
        )
        assertEquals("Sent 3 of 5 files to Alice (2 files couldn't be read)", sentCardCopy(state))
    }

    @Test
    fun receivedFullCopy() {
        val state = PeerTransferState.Received(
            peer,
            received = 5,
            total = 5,
            perFile = emptyList(),
            partialReason = null,
        )
        assertEquals("Received 5 files from Alice — tap to open", receivedCardCopy(state))
    }

    @Test
    fun receivedSenderCancelledCopy() {
        val state = PeerTransferState.Received(
            peer,
            received = 3,
            total = 5,
            perFile = emptyList(),
            partialReason = PartialOutcome.SenderCancelled,
        )
        assertEquals("Received 3 files from Alice — sender cancelled.", receivedCardCopy(state))
    }

    @Test
    fun receivedReceiverCancelledCopy() {
        val state = PeerTransferState.Received(
            peer,
            received = 3,
            total = 5,
            perFile = emptyList(),
            partialReason = PartialOutcome.ReceiverCancelled,
        )
        assertEquals("Received 3 files from Alice — you cancelled.", receivedCardCopy(state))
    }

    @Test
    fun receivedConnectionLostCopy() {
        val state = PeerTransferState.Received(
            peer,
            received = 3,
            total = 5,
            perFile = emptyList(),
            partialReason = PartialOutcome.ConnectionLost,
        )
        assertEquals("Received 3 files from Alice — connection lost.", receivedCardCopy(state))
    }

    @Test
    fun receiverCancelledIsLockedCopy() {
        val state = PeerTransferState.Received(
            peer,
            received = 2,
            total = 5,
            perFile = emptyList(),
            partialReason = PartialOutcome.ReceiverCancelled,
        )
        assertEquals("Received 2 files from Alice — you cancelled.", receivedCardCopy(state))
    }

    @Test
    fun errorNetworkLostCopy() {
        val state = PeerTransferState.Error(
            peer,
            reason = TransferErrorReason.NetworkLost,
            sent = 0,
            perFile = emptyList(),
        )
        assertEquals("Connection lost. Try again when you're back on Wi-Fi.", errorCardCopy(state))
    }

    @Test
    fun errorPeerUnreachableCopy() {
        val state = PeerTransferState.Error(
            peer,
            reason = TransferErrorReason.PeerUnreachable,
            sent = 0,
            perFile = emptyList(),
        )
        assertEquals("Alice is no longer reachable. Try again.", errorCardCopy(state))
    }

    @Test
    fun errorReceiverWriteFailedCopy() {
        val state = PeerTransferState.Error(
            peer,
            reason = TransferErrorReason.ReceiverWriteFailed,
            sent = 0,
            perFile = emptyList(),
        )
        assertEquals("Couldn't save on Alice. Free up space and try again.", errorCardCopy(state))
    }

    @Test
    fun errorAllFilesFailedCopy() {
        val state = PeerTransferState.Error(
            peer,
            reason = TransferErrorReason.AllFilesFailed,
            sent = 0,
            perFile = emptyList(),
        )
        assertEquals("Couldn't send to Alice. Try again.", errorCardCopy(state))
    }

    @Test
    fun errorReceiverSuspendedCopy() {
        val state = PeerTransferState.Error(
            peer,
            reason = TransferErrorReason.ReceiverSuspended,
            sent = 0,
            perFile = emptyList(),
        )
        assertEquals("Transfer from Alice was interrupted. Ask Alice to send again.", errorCardCopy(state))
    }

    @Test
    fun cancelledCleanCopy() {
        val state = PeerTransferState.Cancelled(peer, sent = 0, remaining = emptyList(), perFile = emptyList())
        assertEquals("Cancelled", cancelledCardCopy(state))
    }

    @Test
    fun cancelledPartialCopy() {
        val state = PeerTransferState.Cancelled(
            peer,
            sent = 3,
            remaining = listOf("a.txt", "b.txt"),
            perFile = emptyList(),
        )
        assertEquals("Cancelled. 3 files were received before cancel. 2 were not sent.", cancelledCardCopy(state))
    }

    @Test
    fun reconnectingCopy() {
        val state = PeerTransferState.Reconnecting(
            peer = peer,
            direction = com.tubetoast.tether.presentation.transfer.Direction.Outbound,
            remainingSeconds = 12,
            snapshotBeforeDrop = PeerTransferState.Idle(peer),
        )
        assertEquals("Reconnecting to Alice… (12s)", reconnectingCardCopy(state))
    }
}
