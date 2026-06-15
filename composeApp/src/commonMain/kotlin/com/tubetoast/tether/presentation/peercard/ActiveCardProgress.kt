package com.tubetoast.tether.presentation.peercard

import com.tubetoast.tether.presentation.transfer.fractionOf
import com.tubetoast.tether.transfer.PeerTransferState

data class ActiveCardProgress(
    val progress: Float,
    val indeterminate: Boolean,
)

fun outboundCardProgress(state: PeerTransferState.ActiveOutbound): ActiveCardProgress {
    val total = state.totalBytes
    val totalKnown = total != null && total > 0L
    val sentBytes = when (state) {
        is PeerTransferState.ActiveOutbound.Preparing -> state.sentBytes
        is PeerTransferState.ActiveOutbound.Sending -> state.sentBytes
        is PeerTransferState.ActiveOutbound.Claimed -> null
    }
    return ActiveCardProgress(
        progress = sentBytes?.fractionOf(total) ?: 0f,
        indeterminate = state is PeerTransferState.ActiveOutbound.Preparing || !totalKnown,
    )
}

fun inboundCardProgress(state: PeerTransferState.ActiveInbound): ActiveCardProgress {
    val fraction = state.receivedBytes.fractionOf(state.totalBytes)
    return ActiveCardProgress(
        progress = fraction ?: 0f,
        indeterminate = fraction == null,
    )
}
