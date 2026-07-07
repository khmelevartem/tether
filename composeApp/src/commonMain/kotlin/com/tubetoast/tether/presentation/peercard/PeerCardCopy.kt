package com.tubetoast.tether.presentation.peercard

import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.PartialOutcome
import com.tubetoast.tether.transfer.PeerTransferState
import com.tubetoast.tether.transfer.TransferErrorReason

fun sentCardCopy(state: PeerTransferState.Sent, device: Device): String {
    val name = device.name
    return when (val reason = state.partialReason) {
        null -> "Sent ${state.sent} files to $name"
        PartialOutcome.ReceiverCancelled ->
            "Sent ${state.sent} of ${state.total} files to $name (transfer was cancelled)"
        PartialOutcome.SenderCancelled ->
            "Sent ${state.sent} of ${state.total} files to $name (transfer was cancelled)"
        PartialOutcome.ConnectionLost ->
            "Sent ${state.sent} of ${state.total} files to $name (connection lost)"
        PartialOutcome.PeerUnreachable ->
            "Sent ${state.sent} of ${state.total} files to $name (connection lost)"
        PartialOutcome.ReceiverWriteFailed ->
            "Sent ${state.sent} of ${state.total} files to $name (connection lost)"
        is PartialOutcome.FilesUnreadable ->
            "Sent ${state.sent} of ${state.total} files to $name (${reason.count} files couldn't be read)"
    }
}

fun receivedCardCopy(state: PeerTransferState.Received, device: Device): String {
    val name = device.name
    return when (state.partialReason) {
        null -> "Received ${state.received} files from $name — tap to open"
        PartialOutcome.SenderCancelled -> "Received ${state.received} files from $name — sender cancelled."
        PartialOutcome.ReceiverCancelled -> "Received ${state.received} files from $name — you cancelled."
        PartialOutcome.ConnectionLost -> "Received ${state.received} files from $name — connection lost."
        PartialOutcome.PeerUnreachable -> "Received ${state.received} files from $name — connection lost."
        PartialOutcome.ReceiverWriteFailed -> "Received ${state.received} files from $name — connection lost."
        is PartialOutcome.FilesUnreadable -> "Received ${state.received} files from $name — connection lost."
    }
}

fun errorCardCopy(state: PeerTransferState.Error, device: Device): String {
    val name = device.name
    return when (state.reason) {
        TransferErrorReason.NetworkLost -> "Connection lost. Try again when you're back on Wi-Fi."
        TransferErrorReason.PeerUnreachable -> "$name is no longer reachable. Try again."
        TransferErrorReason.ReceiverWriteFailed -> "Couldn't save on $name. Free up space and try again."
        TransferErrorReason.AllFilesFailed -> "Couldn't send to $name. Try again."
        TransferErrorReason.ReceiverSuspended -> "Transfer from $name was interrupted. Ask $name to send again."
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

fun reconnectingCardCopy(remainingSeconds: Int, device: Device): String =
    "Reconnecting to ${device.name}… (${remainingSeconds}s)"
