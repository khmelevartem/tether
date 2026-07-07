package com.tubetoast.tether.presentation.transfer

import com.tubetoast.tether.transfer.PeerTransferState

fun aggregateStripCopy(sent: Int, total: Int, failed: Int): String {
    val base = "$sent of $total sent"
    return if (failed > 0) "$base · $failed failed" else base
}

fun detailsSubtitleCopy(state: PeerTransferState, peerName: String): String = when (state) {
    is PeerTransferState.ActiveOutbound -> when (state) {
        is PeerTransferState.ActiveOutbound.Preparing -> "Preparing ${state.currentIndex + 1} of ${state.totalFiles}…"
        is PeerTransferState.ActiveOutbound.Sending -> "Sending ${state.currentIndex + 1} of ${state.totalFiles}…"
        is PeerTransferState.ActiveOutbound.Claimed -> "Sending 1 of ${state.totalFiles}…"
    }
    is PeerTransferState.ActiveInbound -> when (state.link) {
        is PeerTransferState.InboundLink.Connected -> "Receiving ${state.currentIndex + 1} of ${state.totalFiles}…"
        is PeerTransferState.InboundLink.Reconnecting -> "Reconnecting…"
    }
    is PeerTransferState.Sent ->
        "Sent ${state.sent} of ${state.total} files"
    is PeerTransferState.Received ->
        "Received ${state.received} of ${state.total} files"
    is PeerTransferState.Cancelled ->
        "Cancelled"
    is PeerTransferState.Error ->
        "Error — $peerName"
    is PeerTransferState.Reconnecting ->
        "Reconnecting…"
    is PeerTransferState.Idle ->
        peerName
}
