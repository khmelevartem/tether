package com.tubetoast.tether.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.tubetoast.tether.presentation.banners.BannersSection
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
            val peerList = component.peerListComponent
            val pending by component.bannersComponent.pendingSummary.collectAsState()

            Column(modifier = Modifier.fillMaxSize()) {
                BannersSection(
                    component = component.bannersComponent,
                    modifier = Modifier.fillMaxWidth(),
                )
                Children(stack = stack) { child ->
                    when (val instance = child.instance) {
                        is RootComponent.Child.PeerListChild -> PeerListScreen(
                            component = instance.component,
                            hasPending = pending != null,
                            peerCallbacksFor = { peer ->
                                rememberPeerCallbacks(peer, peerList, component)
                            },
                            autoSendEnabledFor = { peer ->
                                peerList
                                    .peerTransferComponent(peer)
                                    ?.observeAutoSend()
                                    ?.collectAsState(initial = false)
                                    ?.value
                                    ?: false
                            },
                            // TODO(#192): wire Android FilePicker actual (ACTION_OPEN_DOCUMENT / ACTION_OPEN_DOCUMENT_TREE; ContentResolver byte streams)
                            // TODO(#193): wire Desktop FilePicker actual (JFileChooser or nativefiledialog; FileInputStream)
                            // TODO(#194): wire iOS FilePicker actual (UIDocumentPickerViewController; NSURL → openReadChannel)
                            onPickerPick = { _, _ -> },
                        )
                        is RootComponent.Child.TransferDetailsChild -> TransferDetailsScreen(instance.component)
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberPeerCallbacks(
    peer: PeerIdentity,
    peerListComponent: PeerListComponent,
    rootComponent: RootComponent,
): PeerCardCallbacks {
    val peerComponent = peerListComponent.peerTransferComponent(peer) ?: return PeerCardCallbacks(
        onToggleExpand = {},
        onToggleAutoSend = {},
        onCancel = {},
        onDismiss = {},
        onRetry = {},
        onShowDetails = {},
        onOpenFiles = {},
        onClick = null,
    )
    val hasPending = rootComponent.bannersComponent.pendingSummary
        .collectAsState()
        .value != null
    return PeerCardCallbacks(
        onToggleExpand = peerComponent::toggleExpanded,
        onToggleAutoSend = peerComponent::setAutoSend,
        onCancel = peerComponent::onCancel,
        onDismiss = peerComponent::onDismiss,
        onRetry = peerComponent::onRetry,
        onShowDetails = { rootComponent.showTransferDetails(peer) },
        onOpenFiles = {
            // TODO(#192): Android — Intent.ACTION_VIEW to FileProvider URI for received folder
            // TODO(#193): Desktop — Desktop.open() / xdg-open / Finder reveal
            // TODO(#194): iOS — UIApplication.shared.open(Files-app deep link); fallback hint per UX brief §State 6
        },
        onClick = if (hasPending) peerComponent::onCardClick else null,
    )
}
