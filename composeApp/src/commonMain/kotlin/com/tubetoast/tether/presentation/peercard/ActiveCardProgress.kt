package com.tubetoast.tether.presentation.peercard

import com.tubetoast.tether.transfer.PeerTransferState

data class ActiveCardProgress(
    val progress: Float,
    val indeterminate: Boolean,
)

fun outboundCardProgress(state: PeerTransferState.ActiveOutbound): ActiveCardProgress {
    val sending = state as? PeerTransferState.ActiveOutbound.Sending
    val total = state.totalBytes
    val indeterminate = sending?.preparing == true || total == null || total <= 0L
    val progress = if (!indeterminate && sending != null && total != null) {
        (sending.sentBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    return ActiveCardProgress(progress = progress, indeterminate = indeterminate)
}

fun inboundCardProgress(state: PeerTransferState.ActiveInbound): ActiveCardProgress {
    val total = state.totalBytes
    val indeterminate = total == null || total <= 0L
    val progress = if (!indeterminate && total != null) {
        (state.receivedBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    return ActiveCardProgress(progress = progress, indeterminate = indeterminate)
}
