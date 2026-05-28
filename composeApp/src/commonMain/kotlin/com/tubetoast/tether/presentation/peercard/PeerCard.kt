package com.tubetoast.tether.presentation.peercard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tubetoast.tether.presentation.transfer.PeerTransferState
import com.tubetoast.tether.protocol.Device

@Composable
fun PeerCard(
    state: PeerTransferState,
    isOnline: Boolean,
    device: Device,
    callbacks: PeerCardCallbacks,
    modifier: Modifier = Modifier,
    isAutoSendEnabled: Boolean = false,
) {
    when (state) {
        is PeerTransferState.Idle -> PeerCardIdle(
            state = state,
            device = device,
            isOnline = isOnline,
            isAutoSendEnabled = isAutoSendEnabled,
            callbacks = callbacks,
            modifier = modifier,
        )
        is PeerTransferState.ActiveOutbound -> PeerCardActiveOutbound(
            state = state,
            device = device,
            callbacks = callbacks,
            modifier = modifier,
        )
        is PeerTransferState.ActiveInbound -> PeerCardActiveInbound(
            state = state,
            device = device,
            callbacks = callbacks,
            modifier = modifier,
        )
        is PeerTransferState.Reconnecting -> PeerCardReconnecting(
            state = state,
            device = device,
            modifier = modifier,
        )
        is PeerTransferState.Sent -> PeerCardSent(
            state = state,
            device = device,
            callbacks = callbacks,
            modifier = modifier,
        )
        is PeerTransferState.Received -> PeerCardReceived(
            state = state,
            device = device,
            callbacks = callbacks,
            modifier = modifier,
        )
        is PeerTransferState.Cancelled -> PeerCardCancelled(
            state = state,
            device = device,
            callbacks = callbacks,
            modifier = modifier,
        )
        is PeerTransferState.Error -> PeerCardError(
            state = state,
            device = device,
            isOnline = isOnline,
            callbacks = callbacks,
            modifier = modifier,
        )
    }
}
