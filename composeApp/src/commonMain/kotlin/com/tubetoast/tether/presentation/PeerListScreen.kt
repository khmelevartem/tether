package com.tubetoast.tether.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.tubetoast.tether.presentation.banners.BannersSection
import com.tubetoast.tether.presentation.peer.Peer
import com.tubetoast.tether.presentation.peercard.PeerCard
import com.tubetoast.tether.presentation.peercard.PeerCardCallbacks
import com.tubetoast.tether.presentation.peercard.PeerCardContent
import com.tubetoast.tether.presentation.sheets.MobilePickerChooserSheet
import com.tubetoast.tether.presentation.transfer.PeerCardState
import com.tubetoast.tether.transfer.PeerTransferState
import com.tubetoast.tether.transfer.toPeerIdentity
import com.tubetoast.tether.ui.designsystem.BodyText
import com.tubetoast.tether.ui.designsystem.BrandMark
import com.tubetoast.tether.ui.designsystem.BrandMarkState
import com.tubetoast.tether.ui.preview.PreviewFixtures
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.preview.TransferPreviewFixtures
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun PeerListScreen(component: PeerListComponent, modifier: Modifier = Modifier) {
    val state by component.state.subscribeAsState()

    Column(modifier = modifier.fillMaxSize()) {
        BannersSection(
            component = component.bannersComponent,
            modifier = Modifier.fillMaxWidth(),
        )
        PeerListContent(rows = state.rows)
    }
}

@Composable
private fun PeerListContent(
    rows: List<PeerRow>,
    modifier: Modifier = Modifier,
    showMobileChooser: Boolean = IsMobileChooserPlatform,
) {
    val spacing = TetherTheme.spacing

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
                BodyText(
                    text = "Ищем устройства в сети…",
                    color = TetherTheme.colors.textMuted,
                    modifier = Modifier.padding(top = spacing.lg),
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(rows, key = { it.peer.device.id }) { row ->
                    val peerComponent = row.transferComponent
                    val transferState by peerComponent.state.subscribeAsState()
                    val tapAction: () -> Unit = if (transferState.transfer is PeerTransferState.Idle &&
                        showMobileChooser
                    ) {
                        {
                            triggerClick = peerComponent::onCardClick
                            sheetState.targetDetent = SheetDetent.FullyExpanded
                        }
                    } else {
                        peerComponent::onCardClick
                    }
                    val cardModifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.lg, vertical = spacing.sm)
                        .clip(TetherTheme.shapes.md)
                        .clickable(onClick = tapAction)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Tap to interact with ${row.peer.device.name}"
                        }
                    PeerCard(
                        component = peerComponent,
                        modifier = cardModifier,
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

private data class PeerCardPreviewSpec(
    val peer: Peer,
    val transferState: PeerCardState,
)

@Preview(name = "PeerList — discovering empty")
@Composable
private fun PreviewDiscovering(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerListContentPreview(specs = emptyList())
    }

@Preview(name = "PeerList — single device")
@Composable
private fun PreviewSingleDevice(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        val device = PreviewFixtures.singleDevice.first()
        PeerListContentPreview(
            specs = listOf(
                PeerCardPreviewSpec(
                    peer = Peer(id = device.toPeerIdentity(), device = device),
                    transferState = PeerCardState(TransferPreviewFixtures.idleCollapsed, expanded = false),
                ),
            ),
        )
    }

@Preview(name = "PeerList — multiple devices")
@Composable
private fun PreviewMultipleDevices(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerListContentPreview(
            specs = PreviewFixtures.multipleDevices.mapIndexed { index, device ->
                PeerCardPreviewSpec(
                    peer = Peer(id = device.toPeerIdentity(), device = device, isOnline = index != 2),
                    transferState = PeerCardState(
                        transfer = when (index) {
                            0 -> TransferPreviewFixtures.activeOutbound
                            1 -> TransferPreviewFixtures.idleCollapsed
                            else -> TransferPreviewFixtures.sentFull
                        },
                        expanded = false,
                    ),
                )
            },
        )
    }

@Preview(name = "PeerList — pending (tap any device to send)")
@Composable
private fun PreviewPendingState(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        val device = PreviewFixtures.singleDevice.first()
        PeerListContentPreview(
            specs = listOf(
                PeerCardPreviewSpec(
                    peer = Peer(id = device.toPeerIdentity(), device = device),
                    transferState = PeerCardState(TransferPreviewFixtures.idleCollapsed, expanded = false),
                ),
            ),
        )
    }

@Composable
private fun PeerListContentPreview(specs: List<PeerCardPreviewSpec>) {
    val spacing = TetherTheme.spacing

    Column(modifier = Modifier.fillMaxSize()) {
        if (specs.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BrandMark(state = BrandMarkState.Searching)
                BodyText(
                    text = "Ищем устройства в сети…",
                    color = TetherTheme.colors.textMuted,
                    modifier = Modifier.padding(top = spacing.lg),
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(specs, key = { it.peer.device.id }) { spec ->
                    PeerCardContent(
                        state = spec.transferState,
                        isOnline = spec.peer.isOnline,
                        device = spec.peer.device,
                        callbacks = previewCallbacks(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.lg, vertical = spacing.sm),
                    )
                }
            }
        }
    }
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
