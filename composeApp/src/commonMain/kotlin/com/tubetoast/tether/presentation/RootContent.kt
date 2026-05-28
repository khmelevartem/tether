package com.tubetoast.tether.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.tubetoast.tether.presentation.peercard.PeerCardCallbacks
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun RootContent(component: RootComponent, modifier: Modifier = Modifier) {
    TetherTheme {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(TetherTheme.colors.surface)
                .safeContentPadding(),
        ) {
            val stack by component.stack.subscribeAsState()
            val pendingFiles by component.pendingFiles.subscribeAsState()
            val dropFeedback by component.dropFeedback.subscribeAsState()

            val hasPending = pendingFiles.fileCount > 0
            val pending = if (hasPending) pendingFiles else null

            Children(stack = stack) { child ->
                when (val instance = child.instance) {
                    is RootComponent.Child.DeviceListChild -> DeviceListScreen(
                        component = instance.component,
                        pending = pending,
                        dropFeedback = dropFeedback,
                        onCancelPending = component::clearPendingFiles,
                        peerCallbacksFor = { peer -> buildPeerCallbacks(peer, component, hasPending) },
                        // TODO(#follow-up): wire platform picker
                        onPickerPick = { _, _ -> },
                    )
                    is RootComponent.Child.TransferDetailsChild -> TransferDetailsScreen(instance.component)
                }
            }
        }
    }
}

private fun buildPeerCallbacks(
    peer: PeerIdentity,
    rootComponent: RootComponent,
    hasPending: Boolean,
): PeerCardCallbacks {
    val peerComponent = rootComponent.registry.get(peer)
    return PeerCardCallbacks(
        onToggleExpand = peerComponent::toggleExpanded,
        onToggleAutoSend = {},
        onShowAutoSendInfo = {},
        onCancel = peerComponent::onCancel,
        onDismiss = peerComponent::onDismiss,
        onRetry = peerComponent::onRetry,
        onShowDetails = { rootComponent.showTransferDetails(peer) },
        onOpenFiles = {
            // TODO(#follow-up): implement OS deep-link per platform
        },
        onClick = if (hasPending) {
            { rootComponent.onPeerTapped(peer) }
        } else {
            null
        },
    )
}
