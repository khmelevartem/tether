package com.tubetoast.tether.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.tubetoast.tether.presentation.peercard.PeerCardCallbacks
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.ui.theme.TetherTheme
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.wrapper.withTag

private val log = KydraLog.withTag(default = "RootContent")

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
            val pending by component.pendingFilesRepository.summary.collectAsState()
            val dropFeedback by peerList.dropFeedback.subscribeAsState()

            val hasPending = pending != null

            Children(stack = stack) { child ->
                when (val instance = child.instance) {
                    is RootComponent.Child.PeerListChild -> PeerListScreen(
                        component = instance.component,
                        pending = pending,
                        dropFeedback = dropFeedback,
                        onCancelPending = component.pendingFilesRepository::clear,
                        peerCallbacksFor = { peer ->
                            rememberPeerCallbacks(peer, peerList, component, hasPending)
                        },
                        autoSendEnabledFor = { peer ->
                            peerList.observeAutoSend(peer).collectAsState(initial = false).value
                        },
                        // TODO(#192): wire Android FilePicker actual (ACTION_OPEN_DOCUMENT / ACTION_OPEN_DOCUMENT_TREE; ContentResolver byte streams)
                        // TODO(#193): wire Desktop FilePicker actual (JFileChooser or nativefiledialog; FileInputStream)
                        // TODO(#194): wire iOS FilePicker actual (UIDocumentPickerViewController; NSURL → openReadChannel)
                        onPickerPick = { peer, kind -> log.info { "onPickerPick($peer, $kind)" } },
                    )
                    is RootComponent.Child.TransferDetailsChild -> TransferDetailsScreen(instance.component)
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
    hasPending: Boolean,
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
    return PeerCardCallbacks(
        onToggleExpand = peerComponent::toggleExpanded,
        onToggleAutoSend = { enabled -> peerListComponent.setAutoSend(peer, enabled) },
        onCancel = peerComponent::onCancel,
        onDismiss = peerComponent::onDismiss,
        onRetry = peerComponent::onRetry,
        onShowDetails = { rootComponent.showTransferDetails(peer) },
        onOpenFiles = {
            // TODO(#192): Android — Intent.ACTION_VIEW to FileProvider URI for received folder
            // TODO(#193): Desktop — Desktop.open() / xdg-open / Finder reveal
            // TODO(#194): iOS — UIApplication.shared.open(Files-app deep link); fallback hint per UX brief §State 6
        },
        onClick = if (hasPending) {
            { peerListComponent.onPeerTapped(peer) }
        } else {
            null
        },
    )
}
