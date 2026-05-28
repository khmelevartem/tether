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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.composables.core.SheetDetent
import com.composables.core.rememberModalBottomSheetState
import com.tubetoast.tether.presentation.banners.IosForegroundConstraintBanner
import com.tubetoast.tether.presentation.banners.PendingOutboundBanner
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
    component: DeviceListComponent,
    pending: PendingFilesSummary?,
    dropFeedback: Boolean,
    onCancelPending: () -> Unit,
    peerCallbacksFor: (PeerIdentity) -> PeerCardCallbacks,
    onPickerPick: (PeerIdentity, PickerKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()

    DeviceListContent(
        rows = state.rows,
        pending = pending,
        dropFeedback = dropFeedback,
        onCancelPending = onCancelPending,
        peerCallbacksFor = peerCallbacksFor,
        onPickerPick = onPickerPick,
        modifier = modifier,
    )
}

@Composable
fun DeviceListContent(
    rows: List<DeviceRow>,
    pending: PendingFilesSummary?,
    dropFeedback: Boolean,
    onCancelPending: () -> Unit,
    peerCallbacksFor: (PeerIdentity) -> PeerCardCallbacks,
    onPickerPick: (PeerIdentity, PickerKind) -> Unit,
    modifier: Modifier = Modifier,
    showIosBanner: Boolean = false,
) {
    val spacing = TetherTheme.spacing
    val colors = TetherTheme.colors
    val typography = TetherTheme.typography

    val sheetState = rememberModalBottomSheetState(
        initialDetent = SheetDetent.Hidden,
    )
    // Holds the peer for which the picker sheet was opened; cleared on dismiss.
    var sheetPeer: PeerIdentity? = androidx.compose.runtime
        .remember {
            androidx.compose.runtime.mutableStateOf<PeerIdentity?>(null)
        }.value
    val sheetPeerState = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<PeerIdentity?>(
            null,
        )
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

        IosForegroundConstraintBanner(
            // TODO(#follow-up): wire to iOS-only signal; always false on non-iOS platforms
            visible = showIosBanner,
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
                items(rows, key = { it.device.id }) { row ->
                    val peer = row.device.toPeerIdentity()
                    val tapAction: (() -> Unit)? = when {
                        pending != null -> peerCallbacksFor(peer).let { cbs -> { cbs.onClick?.invoke() } }
                        row.transferState is PeerTransferState.Idle ->
                            (
                                {
                                    sheetPeerState.value = peer
                                    sheetState.targetDetent = SheetDetent.FullyExpanded
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
                                    .clickable(onClick = tapAction)
                                    .semantics {
                                        role = Role.Button
                                        contentDescription = if (pending != null) {
                                            "Send to ${row.device.name}"
                                        } else {
                                            "Pick files to send to ${row.device.name}"
                                        }
                                    }
                            } else {
                                Modifier
                            },
                        )
                    PeerCard(
                        state = row.transferState,
                        isOnline = row.isOnline,
                        device = row.device,
                        callbacks = peerCallbacksFor(peer),
                        modifier = cardModifier,
                    )
                }
            }
        }
    }

    sheetPeerState.value?.let { triggerPeer ->
        MobilePickerChooserSheet(
            sheetState = sheetState,
            onPickPhotos = {
                sheetPeerState.value = null
                onPickerPick(triggerPeer, PickerKind.Photos)
            },
            onPickFiles = {
                sheetPeerState.value = null
                onPickerPick(triggerPeer, PickerKind.Files)
            },
            onPickFolder = {
                sheetPeerState.value = null
                onPickerPick(triggerPeer, PickerKind.Folder)
            },
            onDismiss = { sheetPeerState.value = null },
        )
    }
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
        DeviceListContent(
            rows = listOf(
                DeviceRow(
                    device = PreviewFixtures.singleDevice.first(),
                    transferState = TransferPreviewFixtures.idleCollapsed,
                    isOnline = true,
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
                DeviceRow(
                    device = device,
                    transferState = when (index) {
                        0 -> TransferPreviewFixtures.activeOutbound
                        1 -> TransferPreviewFixtures.idleCollapsed
                        else -> TransferPreviewFixtures.sentFull
                    },
                    isOnline = index != 2,
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
        DeviceListContent(
            rows = listOf(
                DeviceRow(
                    device = PreviewFixtures.singleDevice.first(),
                    transferState = TransferPreviewFixtures.idleCollapsed,
                    isOnline = true,
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
        DeviceListContent(
            rows = listOf(
                DeviceRow(
                    device = PreviewFixtures.singleDevice.first(),
                    transferState = TransferPreviewFixtures.activeOutbound,
                    isOnline = true,
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
        DeviceListContent(
            rows = listOf(
                DeviceRow(
                    device = PreviewFixtures.singleDevice.first(),
                    transferState = TransferPreviewFixtures.activeInbound,
                    isOnline = true,
                ),
            ),
            pending = null,
            dropFeedback = false,
            showIosBanner = true,
            onCancelPending = {},
            peerCallbacksFor = { previewCallbacks() },
            onPickerPick = { _, _ -> },
        )
    }

private fun previewCallbacks() = PeerCardCallbacks(
    onToggleExpand = {},
    onToggleAutoSend = {},
    onShowAutoSendInfo = {},
    onCancel = {},
    onDismiss = {},
    onRetry = {},
    onShowDetails = {},
    onOpenFiles = {},
    onClick = null,
)
