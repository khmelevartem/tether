package com.tubetoast.tether.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.composables.core.SheetDetent
import com.composables.core.rememberModalBottomSheetState
import com.tubetoast.tether.foundation.IsMobileChooserPlatform
import com.tubetoast.tether.presentation.peer.Peer
import com.tubetoast.tether.presentation.peercard.PeerCard
import com.tubetoast.tether.presentation.peercard.PeerCardCallbacks
import com.tubetoast.tether.presentation.sheets.MobilePickerChooserSheet
import com.tubetoast.tether.presentation.transfer.PeerTransferState
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.transfer.toPeerIdentity
import com.tubetoast.tether.ui.components.BrandMark
import com.tubetoast.tether.ui.components.BrandMarkState
import com.tubetoast.tether.ui.preview.PreviewFixtures
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.preview.TransferPreviewFixtures
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun PeerListScreen(component: PeerListComponent, modifier: Modifier = Modifier) {
    val state by component.state.subscribeAsState()

    PeerListContent(
        rows = state.rows,
        peerCallbacksFor = { peer -> rememberPeerCallbacks(peer, component) },
        autoSendEnabledFor = { peer ->
            component
                .peerTransferComponent(peer)
                ?.observeAutoSend()
                ?.collectAsState(initial = false)
                ?.value
                ?: false
        },
        modifier = modifier,
    )
}

@Composable
private fun rememberPeerCallbacks(
    peer: PeerIdentity,
    component: PeerListComponent,
): PeerCardCallbacks {
    val peerComponent = component.peerTransferComponent(peer) ?: return PeerCardCallbacks(
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
        onToggleAutoSend = peerComponent::setAutoSend,
        onCancel = peerComponent::onCancel,
        onDismiss = peerComponent::onDismiss,
        onRetry = peerComponent::onRetry,
        onShowDetails = peerComponent::onShowDetails,
        onOpenFiles = {
            // TODO(#192): Android — Intent.ACTION_VIEW to FileProvider URI for received folder
            // TODO(#193): Desktop — Desktop.open() / xdg-open / Finder reveal
            // TODO(#194): iOS — UIApplication.shared.open(Files-app deep link); fallback hint per UX brief §State 6
        },
        onClick = peerComponent::onCardClick,
    )
}

@Composable
fun PeerListContent(
    rows: List<PeerRow>,
    peerCallbacksFor: @Composable (PeerIdentity) -> PeerCardCallbacks,
    modifier: Modifier = Modifier,
    autoSendEnabledFor: @Composable (PeerIdentity) -> Boolean = { false },
    showMobileChooser: Boolean = IsMobileChooserPlatform,
) {
    val spacing = TetherTheme.spacing
    val colors = TetherTheme.colors
    val typography = TetherTheme.typography

    val sheetState = rememberModalBottomSheetState(
        initialDetent = SheetDetent.Hidden,
    )
    var triggerClick by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(sheetState.currentDetent) {
        if (sheetState.currentDetent == SheetDetent.Hidden) {
            triggerClick = null
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (rows.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BrandMark(state = BrandMarkState.Searching)
                BasicText(
                    text = "Ищем устройства в сети…",
                    style = typography.bodyMedium.copy(color = colors.textMuted),
                    modifier = Modifier.padding(top = spacing.lg),
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(rows, key = { it.peer.device.id }) { row ->
                    val peer = row.peer.id
                    val callbacks = peerCallbacksFor(peer)
                    val tapAction: (() -> Unit)? = when {
                        row.transferState is PeerTransferState.Idle && showMobileChooser -> (
                            {
                                triggerClick = callbacks.onClick
                                sheetState.targetDetent = SheetDetent.FullyExpanded
                            }
                        )
                        callbacks.onClick != null -> callbacks.onClick
                        else -> null
                    }
                    val cardModifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.lg, vertical = spacing.sm)
                        .then(
                            if (tapAction != null) {
                                Modifier
                                    .clip(TetherTheme.shapes.md)
                                    .clickable(onClick = tapAction)
                                    .semantics {
                                        role = Role.Button
                                        contentDescription = "Tap to interact with ${row.peer.device.name}"
                                    }
                            } else {
                                Modifier
                            },
                        )
                    PeerCard(
                        state = row.transferState,
                        isOnline = row.peer.isOnline,
                        device = row.peer.device,
                        callbacks = callbacks,
                        modifier = cardModifier,
                        isAutoSendEnabled = autoSendEnabledFor(peer),
                    )
                }
            }
        }
    }

    MobilePickerChooserSheet(
        sheetState = sheetState,
        onPickPhotos = {
            triggerClick?.invoke()
            sheetState.targetDetent = SheetDetent.Hidden
        },
        onPickFiles = {
            triggerClick?.invoke()
            sheetState.targetDetent = SheetDetent.Hidden
        },
        onPickFolder = {
            triggerClick?.invoke()
            sheetState.targetDetent = SheetDetent.Hidden
        },
        onDismiss = { sheetState.targetDetent = SheetDetent.Hidden },
    )
}

@Preview(name = "PeerList — discovering empty")
@Composable
private fun PreviewDiscovering(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerListContent(
            rows = emptyList(),
            peerCallbacksFor = { previewCallbacks() },
        )
    }

@Preview(name = "PeerList — single device")
@Composable
private fun PreviewSingleDevice(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        val device = PreviewFixtures.singleDevice.first()
        PeerListContent(
            rows = listOf(
                PeerRow(
                    peer = Peer(id = device.toPeerIdentity(), device = device),
                    transferState = TransferPreviewFixtures.idleCollapsed,
                ),
            ),
            peerCallbacksFor = { previewCallbacks() },
        )
    }

@Preview(name = "PeerList — multiple devices")
@Composable
private fun PreviewMultipleDevices(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerListContent(
            rows = PreviewFixtures.multipleDevices.mapIndexed { index, device ->
                PeerRow(
                    peer = Peer(id = device.toPeerIdentity(), device = device, isOnline = index != 2),
                    transferState = when (index) {
                        0 -> TransferPreviewFixtures.activeOutbound
                        1 -> TransferPreviewFixtures.idleCollapsed
                        else -> TransferPreviewFixtures.sentFull
                    },
                )
            },
            peerCallbacksFor = { previewCallbacks() },
        )
    }

@Preview(name = "PeerList — pending (tap any device to send)")
@Composable
private fun PreviewPendingState(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        val device = PreviewFixtures.singleDevice.first()
        PeerListContent(
            rows = listOf(
                PeerRow(
                    peer = Peer(id = device.toPeerIdentity(), device = device),
                    transferState = TransferPreviewFixtures.idleCollapsed,
                ),
            ),
            peerCallbacksFor = { previewCallbacks() },
        )
    }

private fun previewCallbacks() = PeerCardCallbacks(
    onToggleExpand = {},
    onToggleAutoSend = {},
    onCancel = {},
    onDismiss = {},
    onRetry = {},
    onShowDetails = {},
    onOpenFiles = {},
    onClick = null,
)
