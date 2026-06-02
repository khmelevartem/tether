package com.tubetoast.tether.presentation.peercard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.tubetoast.tether.presentation.transfer.PeerCardState
import com.tubetoast.tether.presentation.transfer.PeerTransferComponent
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.PeerTransferState
import com.tubetoast.tether.ui.feature.DevicePlatform
import com.tubetoast.tether.ui.feature.inferDevicePlatform

@Composable
fun PeerCard(
    component: PeerTransferComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()
    val isAutoSendEnabled by component.observeAutoSend().collectAsState(initial = false)
    val devicePlatform = remember(component.peer.device.name) {
        inferDevicePlatform(component.peer.device.name)
    }
    val callbacks = PeerCardCallbacks(
        onToggleExpand = component::toggleExpanded,
        onToggleAutoSend = component::setAutoSend,
        onCancel = component::onCancel,
        onDismiss = component::onDismiss,
        onRetryOutbound = component::onRetryOutbound,
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
        // TODO(#10): wire isPaired from PeerTransferComponent once observeIsPaired() is available
        isPaired = false,
        devicePlatform = devicePlatform,
    )
}

@Composable
internal fun PeerCardContent(
    state: PeerCardState,
    isOnline: Boolean,
    device: Device,
    callbacks: PeerCardCallbacks,
    modifier: Modifier = Modifier,
    isAutoSendEnabled: Boolean = false,
    isPaired: Boolean = false,
    devicePlatform: DevicePlatform? = null,
) {
    when (val transfer = state.transfer) {
        is PeerTransferState.Idle -> PeerCardIdle(
            state = transfer,
            expanded = state.expanded,
            device = device,
            isOnline = isOnline,
            isAutoSendEnabled = isAutoSendEnabled,
            callbacks = callbacks,
            modifier = modifier,
            isPaired = isPaired,
            devicePlatform = devicePlatform,
        )
        is PeerTransferState.ActiveOutbound -> PeerCardActiveOutbound(
            state = transfer,
            device = device,
            callbacks = callbacks,
            modifier = modifier,
        )
        is PeerTransferState.ActiveInbound -> PeerCardActiveInbound(
            state = transfer,
            device = device,
            callbacks = callbacks,
            modifier = modifier,
        )
        is PeerTransferState.Reconnecting -> PeerCardReconnecting(
            state = transfer,
            device = device,
            modifier = modifier,
        )
        is PeerTransferState.Sent -> PeerCardSent(
            state = transfer,
            device = device,
            callbacks = callbacks,
            modifier = modifier,
        )
        is PeerTransferState.Received -> PeerCardReceived(
            state = transfer,
            device = device,
            callbacks = callbacks,
            modifier = modifier,
        )
        is PeerTransferState.Cancelled -> PeerCardCancelled(
            state = transfer,
            device = device,
            callbacks = callbacks,
            modifier = modifier,
        )
        is PeerTransferState.Error -> PeerCardError(
            state = transfer,
            device = device,
            isOnline = isOnline,
            callbacks = callbacks,
            modifier = modifier,
        )
    }
}
