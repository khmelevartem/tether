package com.tubetoast.tether.presentation.peercard

import com.tubetoast.tether.transfer.PartialOutcome
import com.tubetoast.tether.transfer.PeerTransferState
import com.tubetoast.tether.transfer.TransferErrorReason

fun sentCardCopy(state: PeerTransferState.Sent, peerName: String): String =
    when (val reason = state.partialReason) {
        null -> "Sent ${state.sent} files to $peerName"
        PartialOutcome.ReceiverCancelled ->
            "Sent ${state.sent} of ${state.total} files to $peerName (transfer was cancelled)"
        PartialOutcome.SenderCancelled ->
            "Sent ${state.sent} of ${state.total} files to $peerName (transfer was cancelled)"
        PartialOutcome.ConnectionLost ->
            "Sent ${state.sent} of ${state.total} files to $peerName (connection lost)"
        PartialOutcome.PeerUnreachable ->
            "Sent ${state.sent} of ${state.total} files to $peerName (connection lost)"
        PartialOutcome.ReceiverWriteFailed ->
            "Sent ${state.sent} of ${state.total} files to $peerName (connection lost)"
        is PartialOutcome.FilesUnreadable ->
            "Sent ${state.sent} of ${state.total} files to $peerName (${reason.count} files couldn't be read)"
    }

fun receivedCardCopy(state: PeerTransferState.Received, peerName: String): String =
    when (state.partialReason) {
        null -> "Received ${state.received} files from $peerName — tap to open"
        PartialOutcome.SenderCancelled -> "Received ${state.received} files from $peerName — sender cancelled."
        PartialOutcome.ReceiverCancelled -> "Received ${state.received} files from $peerName — you cancelled."
        PartialOutcome.ConnectionLost -> "Received ${state.received} files from $peerName — connection lost."
        PartialOutcome.PeerUnreachable -> "Received ${state.received} files from $peerName — connection lost."
        PartialOutcome.ReceiverWriteFailed -> "Received ${state.received} files from $peerName — connection lost."
        is PartialOutcome.FilesUnreadable -> "Received ${state.received} files from $peerName — connection lost."
    }

fun errorCardCopy(state: PeerTransferState.Error, peerName: String): String =
    when (state.reason) {
        TransferErrorReason.NetworkLost -> "Connection lost. Try again when you're back on Wi-Fi."
        TransferErrorReason.PeerUnreachable -> "$peerName is no longer reachable. Try again."
        TransferErrorReason.ReceiverWriteFailed -> "Couldn't save on $peerName. Free up space and try again."
        TransferErrorReason.AllFilesFailed -> "Couldn't send to $peerName. Try again."
        TransferErrorReason.ReceiverSuspended -> "Transfer from $peerName was interrupted. Ask $peerName to send again."
    }

fun cancelledCardCopy(state: PeerTransferState.Cancelled): String {
    val base = "Cancelled"
    return if (state.sent > 0 || state.remaining.isNotEmpty()) {
        "$base. ${state.sent} files were received before cancel. ${state.remaining.size} were not sent."
    } else {
        base
    }
}

fun reconnectingCardCopy(state: PeerTransferState.Reconnecting, peerName: String): String =
    "Reconnecting to $peerName… (${state.remainingSeconds}s)"
