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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.tubetoast.tether.presentation.transfer.toPeerIdentity
import com.tubetoast.tether.transfer.PeerIdentity
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
    rootComponent: RootComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()
    val pendingFiles by rootComponent.pendingFiles.subscribeAsState()
    val dropFeedback by rootComponent.dropFeedback.subscribeAsState()

    val hasPending = pendingFiles.fileCount > 0

    DeviceListContent(
        rows = state.rows,
        pending = if (hasPending) pendingFiles else null,
        dropFeedback = dropFeedback,
        onCancelPending = rootComponent::clearPendingFiles,
        onPeerTap = { peer ->
            if (hasPending) {
                rootComponent.onPeerTapped(peer)
            } else {
                rootComponent.showTransferDetails(peer)
            }
        },
        onPickerPick = {},
        modifier = modifier,
    )
}

@Composable
fun DeviceListContent(
    rows: List<DeviceRow>,
    pending: PendingFilesSummary?,
    dropFeedback: Boolean,
    onCancelPending: () -> Unit,
    onPeerTap: (PeerIdentity) -> Unit,
    onPickerPick: (PickerKind) -> Unit,
    modifier: Modifier = Modifier,
    showIosBanner: Boolean = false,
) {
    val spacing = TetherTheme.spacing
    val colors = TetherTheme.colors
    val typography = TetherTheme.typography

    var sheetOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        initialDetent = SheetDetent.Hidden,
    )

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
                        pending != null -> ({ onPeerTap(peer) })
                        row.transferState is com.tubetoast.tether.presentation.transfer.PeerTransferState.Idle ->
                            ({ sheetOpen = true })
                        else -> null
                    }
                    val cardModifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.lg, vertical = spacing.sm)
                        .then(
                            if (tapAction != null) {
                                Modifier
                                    .clickable(
                                        onClick = tapAction,
                                    ).semantics {
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
                        callbacks = peerCardCallbacks(
                            peer = peer,
                            onPeerTap = onPeerTap,
                        ),
                        modifier = cardModifier,
                    )
                }
            }
        }
    }

    if (sheetOpen) {
        MobilePickerChooserSheet(
            sheetState = sheetState,
            onPickPhotos = {
                sheetOpen = false
                onPickerPick(PickerKind.Photos)
            },
            onPickFiles = {
                sheetOpen = false
                onPickerPick(PickerKind.Files)
            },
            onPickFolder = {
                sheetOpen = false
                onPickerPick(PickerKind.Folder)
            },
            onDismiss = { sheetOpen = false },
        )
    }
}

private fun peerCardCallbacks(
    peer: PeerIdentity,
    onPeerTap: (PeerIdentity) -> Unit,
): PeerCardCallbacks = PeerCardCallbacks(
    onToggleExpand = {},
    onToggleAutoSend = {},
    onShowAutoSendInfo = {},
    onCancel = { onPeerTap(peer) },
    onDismiss = { onPeerTap(peer) },
    onRetry = { onPeerTap(peer) },
    onShowDetails = { onPeerTap(peer) },
    onOpenFiles = {},
)

@Preview(name = "DeviceList — discovering empty")
@Composable
private fun PreviewDiscovering(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        DeviceListContent(
            rows = emptyList(),
            pending = null,
            dropFeedback = false,
            onCancelPending = {},
            onPeerTap = {},
            onPickerPick = {},
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
            onPeerTap = {},
            onPickerPick = {},
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
            onPeerTap = {},
            onPickerPick = {},
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
            onPeerTap = {},
            onPickerPick = {},
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
            onPeerTap = {},
            onPickerPick = {},
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
            onPeerTap = {},
            onPickerPick = {},
        )
    }
