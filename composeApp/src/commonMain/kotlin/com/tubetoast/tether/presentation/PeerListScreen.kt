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
fun PeerListScreen(
    component: PeerListComponent,
    peerCallbacksFor: @Composable (PeerIdentity) -> PeerCardCallbacks,
    onPickerPick: (PeerIdentity, PickerKind) -> Unit,
    modifier: Modifier = Modifier,
    autoSendEnabledFor: @Composable (PeerIdentity) -> Boolean = { false },
    hasPending: Boolean = false,
) {
    val state by component.state.subscribeAsState()

    PeerListContent(
        rows = state.rows,
        peerCallbacksFor = peerCallbacksFor,
        autoSendEnabledFor = autoSendEnabledFor,
        onPickerPick = onPickerPick,
        hasPending = hasPending,
        modifier = modifier,
    )
}

@Composable
fun PeerListContent(
    rows: List<PeerRow>,
    peerCallbacksFor: @Composable (PeerIdentity) -> PeerCardCallbacks,
    onPickerPick: (PeerIdentity, PickerKind) -> Unit,
    modifier: Modifier = Modifier,
    autoSendEnabledFor: @Composable (PeerIdentity) -> Boolean = { false },
    hasPending: Boolean = false,
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
                        hasPending -> peerCallbacksFor(peer).let { cbs -> { cbs.onClick?.invoke() } }
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
                                        contentDescription = if (hasPending) {
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

@Preview(name = "PeerList — discovering empty")
@Composable
private fun PreviewDiscovering(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerListContent(
            rows = emptyList(),
            peerCallbacksFor = { previewCallbacks() },
            onPickerPick = { _, _ -> },
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
            onPickerPick = { _, _ -> },
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
            onPickerPick = { _, _ -> },
        )
    }

@Preview(name = "PeerList — pending (banner in BannersSection above)")
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
            hasPending = true,
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
