package com.tubetoast.tether.presentation.peercard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.tubetoast.tether.presentation.transfer.PeerTransferComponent
import com.tubetoast.tether.presentation.transfer.PeerTransferState
import com.tubetoast.tether.protocol.Device

@Composable
fun PeerCard(
    component: PeerTransferComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()
    val isAutoSendEnabled by component.observeAutoSend().collectAsState(initial = false)
    val callbacks = PeerCardCallbacks(
        onToggleExpand = component::toggleExpanded,
        onToggleAutoSend = component::setAutoSend,
        onCancel = component::onCancel,
        onDismiss = component::onDismiss,
        onRetry = component::onRetry,
        onShowDetails = component::onShowDetails,
        onOpenFiles = {
            // TODO(#192): Android — Intent.ACTION_VIEW to FileProvider URI for received folder
            // TODO(#193): Desktop — Desktop.open() / xdg-open / Finder reveal
            // TODO(#194): iOS — UIApplication.shared.open(Files-app deep link); fallback hint per UX brief §State 6
        },
        onClick = component::onCardClick,
    )
    PeerCardContent(
        state = state,
        isOnline = component.peer.isOnline,
        device = component.peer.device,
        callbacks = callbacks,
        modifier = modifier,
        isAutoSendEnabled = isAutoSendEnabled,
    )
}

@Composable
internal fun PeerCardContent(
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
