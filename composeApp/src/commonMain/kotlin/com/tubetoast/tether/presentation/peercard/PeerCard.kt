package com.tubetoast.tether.presentation.peercard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.tubetoast.tether.presentation.dialogs.LargeSelectionConfirmDialog
import com.tubetoast.tether.presentation.transfer.PeerCardState
import com.tubetoast.tether.presentation.transfer.PeerTransferComponent
import com.tubetoast.tether.protocol.DeviceType
import com.tubetoast.tether.transfer.PeerTransferState

@Composable
fun PeerCard(
    component: PeerTransferComponent,
    modifier: Modifier = Modifier,
    onChooserRequest: (() -> Unit)? = null,
) {
    val state by component.state.subscribeAsState()
    val isAutoSendEnabled by component.observeAutoSend().collectAsState(initial = false)
    val deviceType = component.deviceType

    LaunchedEffect(component) {
        component.chooserRequests.collect { onChooserRequest?.invoke() }
    }

    val cardModifier = modifier
        .fillMaxWidth()
        .clickable(onClick = component::onCardClick)
        .semantics {
            role = Role.Button
            contentDescription = "Tap to interact with ${state.device.name}"
        }

    state.largeConfirm?.let { confirm ->
        LargeSelectionConfirmDialog(
            fileCount = confirm.summary.fileCount,
            totalBytes = confirm.summary.totalBytes,
            peerName = state.device.name,
            dontShowAgain = confirm.dontShowAgain,
            onDontShowAgainToggle = component::onUpdateLargeConfirmDontShowAgain,
            onConfirm = { component.onConfirmLargeSelection(confirm.dontShowAgain) },
            onDismiss = component::onDismissLargeSelection,
        )
    }

    val callbacks = PeerCardCallbacks(
        onToggleExpand = component::toggleExpanded,
        onToggleAutoSend = component::setAutoSend,
        onCancel = component::onCancel,
        onDismiss = component::onDismiss,
        onRetryOutbound = component::onRetryOutbound,
        onShowDetails = component::onShowDetails,
        onOpenFiles = {
            // TODO(#195): per-platform OS open of the received folder —
            //   Android: Intent.ACTION_VIEW to FileProvider URI; Desktop: Desktop.open() / xdg-open / Finder reveal;
            //   iOS: UIApplication.shared.open(Files-app deep link), fallback hint per UX brief §State 6
        },
        onClick = component::onCardClick,
    )
    PeerCardContent(
        state = state,
        callbacks = callbacks,
        modifier = cardModifier,
        isAutoSendEnabled = isAutoSendEnabled,
        // TODO(#10): wire isPaired from PeerTransferComponent once observeIsPaired() is available
        isPaired = false,
        deviceType = deviceType,
    )
}

@Composable
internal fun PeerCardContent(
    state: PeerCardState,
    callbacks: PeerCardCallbacks,
    modifier: Modifier = Modifier,
    isAutoSendEnabled: Boolean = false,
    isPaired: Boolean = false,
    deviceType: DeviceType? = null,
) {
    when (val transfer = state.transfer) {
        is PeerTransferState.Idle -> PeerCardIdle(
            state = transfer,
            expanded = state.expanded,
            device = state.device,
            isOnline = state.isOnline,
            isAutoSendEnabled = isAutoSendEnabled,
            callbacks = callbacks,
            modifier = modifier,
            isPaired = isPaired,
            deviceType = deviceType,
        )
        is PeerTransferState.ActiveOutbound -> PeerCardActiveOutbound(
            state = transfer,
            device = state.device,
            callbacks = callbacks,
            // TODO(#10): wire isPaired from PeerTransferComponent once observeIsPaired() is available
            isPaired = isPaired,
            modifier = modifier,
        )
        is PeerTransferState.ActiveInbound -> PeerCardActiveInbound(
            state = transfer,
            device = state.device,
            callbacks = callbacks,
            // TODO(#10): wire isPaired from PeerTransferComponent once observeIsPaired() is available
            isPaired = isPaired,
            modifier = modifier,
        )
        is PeerTransferState.Reconnecting -> PeerCardReconnecting(
            state = transfer,
            device = state.device,
            // TODO(#10): wire isPaired from PeerTransferComponent once observeIsPaired() is available
            isPaired = isPaired,
            modifier = modifier,
        )
        is PeerTransferState.Sent -> PeerCardSent(
            state = transfer,
            device = state.device,
            callbacks = callbacks,
            // TODO(#10): wire isPaired from PeerTransferComponent once observeIsPaired() is available
            isPaired = isPaired,
            modifier = modifier,
        )
        is PeerTransferState.Received -> PeerCardReceived(
            state = transfer,
            device = state.device,
            callbacks = callbacks,
            // TODO(#10): wire isPaired from PeerTransferComponent once observeIsPaired() is available
            isPaired = isPaired,
            modifier = modifier,
        )
        is PeerTransferState.Cancelled -> PeerCardCancelled(
            state = transfer,
            device = state.device,
            callbacks = callbacks,
            // TODO(#10): wire isPaired from PeerTransferComponent once observeIsPaired() is available
            isPaired = isPaired,
            modifier = modifier,
        )
        is PeerTransferState.Error -> PeerCardError(
            state = transfer,
            device = state.device,
            isOnline = state.isOnline,
            callbacks = callbacks,
            // TODO(#10): wire isPaired from PeerTransferComponent once observeIsPaired() is available
            isPaired = isPaired,
            modifier = modifier,
        )
    }
}
