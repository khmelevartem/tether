package com.tubetoast.tether.presentation.peercard

import com.tubetoast.tether.presentation.transfer.fractionOf
import com.tubetoast.tether.transfer.PeerTransferState

data class ActiveCardProgress(
    val progress: Float,
    val indeterminate: Boolean,
)

fun outboundCardProgress(state: PeerTransferState.ActiveOutbound): ActiveCardProgress {
    val sending = state as? PeerTransferState.ActiveOutbound.Sending
    val total = state.totalBytes
    val totalKnown = total != null && total > 0L
    return ActiveCardProgress(
        progress = sending?.sentBytes?.fractionOf(total) ?: 0f,
        indeterminate = sending?.preparing == true || !totalKnown,
    )
}

fun inboundCardProgress(state: PeerTransferState.ActiveInbound): ActiveCardProgress {
    val fraction = state.receivedBytes.fractionOf(state.totalBytes)
    return ActiveCardProgress(
        progress = fraction ?: 0f,
        indeterminate = fraction == null,
    )
}
