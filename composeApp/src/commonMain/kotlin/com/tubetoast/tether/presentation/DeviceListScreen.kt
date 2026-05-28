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
import com.tubetoast.tether.presentation.banners.ForegroundConstraintBanner
import com.tubetoast.tether.presentation.banners.PendingOutboundBanner
import com.tubetoast.tether.presentation.peer.Peer
import com.tubetoast.tether.presentation.peercard.PeerCard
import com.tubetoast.tether.presentation.peercard.PeerCardCallbacks
import com.tubetoast.tether.presentation.sheets.MobilePickerChooserSheet
import com.tubetoast.tether.presentation.sheets.PickerKind
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
fun DeviceListScreen(
    component: PeerListComponent,
    pending: PendingFilesSummary?,
    dropFeedback: Boolean,
    onCancelPending: () -> Unit,
    peerCallbacksFor: @Composable (PeerIdentity) -> PeerCardCallbacks,
    onPickerPick: (PeerIdentity, PickerKind) -> Unit,
    modifier: Modifier = Modifier,
    autoSendEnabledFor: @Composable (PeerIdentity) -> Boolean = { false },
) {
    val state by component.state.subscribeAsState()

    DeviceListContent(
        rows = state.rows,
        pending = pending,
        dropFeedback = dropFeedback,
        onCancelPending = onCancelPending,
        peerCallbacksFor = peerCallbacksFor,
        autoSendEnabledFor = autoSendEnabledFor,
        onPickerPick = onPickerPick,
        modifier = modifier,
    )
}

@Composable
fun DeviceListContent(
    rows: List<PeerRow>,
    pending: PendingFilesSummary?,
    dropFeedback: Boolean,
    onCancelPending: () -> Unit,
    peerCallbacksFor: @Composable (PeerIdentity) -> PeerCardCallbacks,
    onPickerPick: (PeerIdentity, PickerKind) -> Unit,
    modifier: Modifier = Modifier,
    autoSendEnabledFor: @Composable (PeerIdentity) -> Boolean = { false },
    showForegroundBanner: Boolean = false,
    showMobileChooser: Boolean = IsMobileChooserPlatform,
) {
    val spacing = TetherTheme.spacing
    val colors = TetherTheme.colors
    val typography = TetherTheme.typography

    val sheetState = rememberModalBottomSheetState(
        initialDetent = SheetDetent.Hidden,
    )
    var triggerPeer by remember { mutableStateOf<PeerIdentity?>(null) }

    LaunchedEffect(sheetState.currentDetent) {
        if (sheetState.currentDetent == SheetDetent.Hidden) {
            triggerPeer = null
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (pending != null) {
            PendingOutboundBanner(
                summary = pending,
                dropFeedback = dropFeedback,
                onCancel = onCancelPending,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ForegroundConstraintBanner(
            // TODO(#194): iOS — wire visible = true while any transfer is active (UIApplication foreground state observer)
            //              On Android / Desktop / macOS this banner is permanently hidden per UX brief Platform Deltas
            visible = showForegroundBanner,
            modifier = Modifier.fillMaxWidth(),
        )

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
                    val tapAction: (() -> Unit)? = when {
                        pending != null -> peerCallbacksFor(peer).let { cbs -> { cbs.onClick?.invoke() } }
                        row.transferState is PeerTransferState.Idle && showMobileChooser -> (
                            {
                                triggerPeer = peer
                                sheetState.targetDetent = SheetDetent.FullyExpanded
                            }
                        )
                        row.transferState is PeerTransferState.Idle -> (
                            {
                                onPickerPick(peer, PickerKind.Files)
                            }
                        )
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
                                        contentDescription = if (pending != null) {
                                            "Send to ${row.peer.device.name}"
                                        } else {
                                            "Pick files to send to ${row.peer.device.name}"
                                        }
                                    }
                            } else {
                                Modifier
                            },
                        )
                    PeerCard(
                        state = row.transferState,
                        isOnline = row.peer.isOnline,
                        device = row.peer.device,
                        callbacks = peerCallbacksFor(peer),
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
            triggerPeer?.let { onPickerPick(it, PickerKind.Photos) }
            sheetState.targetDetent = SheetDetent.Hidden
        },
        onPickFiles = {
            triggerPeer?.let { onPickerPick(it, PickerKind.Files) }
            sheetState.targetDetent = SheetDetent.Hidden
        },
        onPickFolder = {
            triggerPeer?.let { onPickerPick(it, PickerKind.Folder) }
            sheetState.targetDetent = SheetDetent.Hidden
        },
        onDismiss = { sheetState.targetDetent = SheetDetent.Hidden },
    )
}

@Preview(name = "DeviceList — discovering empty")
@Composable
private fun PreviewDiscovering(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        DeviceListContent(
            rows = emptyList(),
            pending = null,
            dropFeedback = false,
            onCancelPending = {},
            peerCallbacksFor = { previewCallbacks() },
            onPickerPick = { _, _ -> },
        )
    }

@Preview(name = "DeviceList — single device")
@Composable
private fun PreviewSingleDevice(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        val device = PreviewFixtures.singleDevice.first()
        DeviceListContent(
            rows = listOf(
                PeerRow(
                    peer = Peer(id = device.toPeerIdentity(), device = device),
                    transferState = TransferPreviewFixtures.idleCollapsed,
                ),
            ),
            pending = null,
            dropFeedback = false,
            onCancelPending = {},
            peerCallbacksFor = { previewCallbacks() },
            onPickerPick = { _, _ -> },
        )
    }

@Preview(name = "DeviceList — multiple devices")
@Composable
private fun PreviewMultipleDevices(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        DeviceListContent(
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
            pending = null,
            dropFeedback = false,
            onCancelPending = {},
            peerCallbacksFor = { previewCallbacks() },
            onPickerPick = { _, _ -> },
        )
    }

@Preview(name = "DeviceList — pending banner")
@Composable
private fun PreviewPendingBanner(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        val device = PreviewFixtures.singleDevice.first()
        DeviceListContent(
            rows = listOf(
                PeerRow(
                    peer = Peer(id = device.toPeerIdentity(), device = device),
                    transferState = TransferPreviewFixtures.idleCollapsed,
                ),
            ),
            pending = PendingFilesSummary(3, 52_428_800L),
            dropFeedback = false,
            onCancelPending = {},
            peerCallbacksFor = { previewCallbacks() },
            onPickerPick = { _, _ -> },
        )
    }

@Preview(name = "DeviceList — drop rejected flash")
@Composable
private fun PreviewDropFlash(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        val device = PreviewFixtures.singleDevice.first()
        DeviceListContent(
            rows = listOf(
                PeerRow(
                    peer = Peer(id = device.toPeerIdentity(), device = device),
                    transferState = TransferPreviewFixtures.activeOutbound,
                ),
            ),
            pending = PendingFilesSummary(5, 104_857_600L),
            dropFeedback = true,
            onCancelPending = {},
            peerCallbacksFor = { previewCallbacks() },
            onPickerPick = { _, _ -> },
        )
    }

@Preview(name = "DeviceList — iOS banner")
@Composable
private fun PreviewIosBanner(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        val device = PreviewFixtures.singleDevice.first()
        DeviceListContent(
            rows = listOf(
                PeerRow(
                    peer = Peer(id = device.toPeerIdentity(), device = device),
                    transferState = TransferPreviewFixtures.activeInbound,
                ),
            ),
            pending = null,
            dropFeedback = false,
            showForegroundBanner = true,
            onCancelPending = {},
            peerCallbacksFor = { previewCallbacks() },
            onPickerPick = { _, _ -> },
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
