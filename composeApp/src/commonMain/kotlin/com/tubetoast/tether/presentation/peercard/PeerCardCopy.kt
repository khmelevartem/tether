package com.tubetoast.tether.presentation.peercard

import com.tubetoast.tether.presentation.transfer.PeerTransferState
import com.tubetoast.tether.transfer.PartialOutcome
import com.tubetoast.tether.transfer.TransferErrorReason

fun sentCardCopy(state: PeerTransferState.Sent): String {
    val peer = state.peer.id
    return when (val reason = state.partialReason) {
        null -> "Sent ${state.sent} files to $peer"
        PartialOutcome.ReceiverCancelled ->
            "Sent ${state.sent} of ${state.total} files to $peer (transfer was cancelled)"
        PartialOutcome.SenderCancelled ->
            "Sent ${state.sent} of ${state.total} files to $peer (transfer was cancelled)"
        PartialOutcome.ConnectionLost ->
            "Sent ${state.sent} of ${state.total} files to $peer (connection lost)"
        PartialOutcome.PeerUnreachable ->
            "Sent ${state.sent} of ${state.total} files to $peer (connection lost)"
        PartialOutcome.ReceiverWriteFailed ->
            "Sent ${state.sent} of ${state.total} files to $peer (connection lost)"
        is PartialOutcome.FilesUnreadable ->
            "Sent ${state.sent} of ${state.total} files to $peer (${reason.count} files couldn't be read)"
    }
}

fun receivedCardCopy(state: PeerTransferState.Received): String {
    val peer = state.peer.id
    return when (state.partialReason) {
        null -> "Received ${state.received} files from $peer — tap to open"
        PartialOutcome.SenderCancelled -> "Received ${state.received} files from $peer — sender cancelled."
        PartialOutcome.ReceiverCancelled -> "Received ${state.received} files from $peer — you cancelled."
        PartialOutcome.ConnectionLost -> "Received ${state.received} files from $peer — connection lost."
        PartialOutcome.PeerUnreachable -> "Received ${state.received} files from $peer — connection lost."
        PartialOutcome.ReceiverWriteFailed -> "Received ${state.received} files from $peer — connection lost."
        is PartialOutcome.FilesUnreadable -> "Received ${state.received} files from $peer — connection lost."
    }
}

fun errorCardCopy(state: PeerTransferState.Error): String {
    val peer = state.peer.id
    return when (state.reason) {
        TransferErrorReason.NetworkLost -> "Connection lost. Try again when you're back on Wi-Fi."
        TransferErrorReason.PeerUnreachable -> "$peer is no longer reachable. Try again."
        TransferErrorReason.ReceiverWriteFailed -> "Couldn't save on $peer. Free up space and try again."
        TransferErrorReason.AllFilesFailed -> "Couldn't send to $peer. Try again."
        TransferErrorReason.ReceiverSuspended -> "Transfer from $peer was interrupted. Ask $peer to send again."
    }
}

fun cancelledCardCopy(state: PeerTransferState.Cancelled): String {
    val base = "Cancelled"
    return if (state.sent > 0 || state.remaining.isNotEmpty()) {
        "$base. ${state.sent} files were received before cancel. ${state.remaining.size} were not sent."
    } else {
        base
    }
}

fun reconnectingCardCopy(state: PeerTransferState.Reconnecting): String =
    "Reconnecting to ${state.peer.id}… (${state.remainingSeconds}s)"
