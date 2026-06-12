package com.tubetoast.tether.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.composables.core.SheetDetent
import com.composables.core.rememberModalBottomSheetState
import com.tubetoast.tether.foundation.IsMobileChooserPlatform
import com.tubetoast.tether.presentation.banners.BannersSection
import com.tubetoast.tether.presentation.banners.PendingOutboundBanner
import com.tubetoast.tether.presentation.banners.PendingOutboundBannerState
import com.tubetoast.tether.presentation.devicename.ThisDeviceStripContent
import com.tubetoast.tether.presentation.devicename.ThisDeviceStripScreen
import com.tubetoast.tether.presentation.peercard.PeerCard
import com.tubetoast.tether.presentation.peercard.PeerCardCallbacks
import com.tubetoast.tether.presentation.peercard.PeerCardContent
import com.tubetoast.tether.presentation.sheets.MobilePickerChooserSheet
import com.tubetoast.tether.presentation.transfer.PeerCardState
import com.tubetoast.tether.presentation.transfer.PeerTransferComponent
import com.tubetoast.tether.transfer.PendingFilesSummary
import com.tubetoast.tether.transfer.PickKind
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
    val bannerState by component.bannersComponent.pendingBanner.collectAsState()
    val hasPendingOutbound = bannerState != PendingOutboundBannerState.Hidden

    Column(modifier = modifier.fillMaxSize()) {
        BannersSection(
            component = component.bannersComponent,
            modifier = Modifier.fillMaxWidth(),
        )
        ThisDeviceStripScreen(
            component = component.deviceNameComponent,
            modifier = Modifier.fillMaxWidth(),
        )
        PeerListContent(rows = state.rows, hasPendingOutbound = hasPendingOutbound)
    }
}

@Composable
private fun PeerListContent(
    rows: List<PeerRow>,
    modifier: Modifier = Modifier,
    hasPendingOutbound: Boolean = false,
    showMobileChooser: Boolean = IsMobileChooserPlatform,
) {
    val spacing = TetherTheme.spacing

    val sheetState = rememberModalBottomSheetState(
        initialDetent = SheetDetent.Hidden,
    )
    var pendingPickComponent by remember { mutableStateOf<PeerTransferComponent?>(null) }

    LaunchedEffect(sheetState.currentDetent) {
        if (sheetState.currentDetent == SheetDetent.Hidden) {
            pendingPickComponent = null
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
                items(rows, key = { it.transferComponent.peerId.id }) { row ->
                    val peerComponent = row.transferComponent
                    PeerCard(
                        component = peerComponent,
                        onRequestMobileChooser = if (showMobileChooser && !hasPendingOutbound) {
                            {
                                pendingPickComponent = peerComponent
                                sheetState.targetDetent = SheetDetent.FullyExpanded
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    MobilePickerChooserSheet(
        sheetState = sheetState,
        onPickPhotos = {
            pendingPickComponent?.onPick(PickKind.Photos)
            sheetState.targetDetent = SheetDetent.Hidden
        },
        onPickFiles = {
            pendingPickComponent?.onPick(PickKind.Files)
            sheetState.targetDetent = SheetDetent.Hidden
        },
        onPickFolder = {
            pendingPickComponent?.onPick(PickKind.Folder)
            sheetState.targetDetent = SheetDetent.Hidden
        },
        onDismiss = { sheetState.targetDetent = SheetDetent.Hidden },
    )
}

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
                PeerCardState(
                    transfer = TransferPreviewFixtures.idleCollapsed,
                    expanded = false,
                    isOnline = true,
                    device = device,
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
                PeerCardState(
                    transfer = when (index) {
                        0 -> TransferPreviewFixtures.activeOutbound
                        1 -> TransferPreviewFixtures.idleCollapsed
                        else -> TransferPreviewFixtures.sentFull
                    },
                    expanded = false,
                    isOnline = index != 2,
                    device = device,
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
                PeerCardState(
                    transfer = TransferPreviewFixtures.idleCollapsed,
                    expanded = false,
                    isOnline = true,
                    device = device,
                ),
            ),
            hasPendingOutbound = true,
        )
    }

@Composable
private fun PeerListContentPreview(
    specs: List<PeerCardState>,
    hasPendingOutbound: Boolean = false,
) {
    val spacing = TetherTheme.spacing

    Column(modifier = Modifier.fillMaxSize()) {
        ThisDeviceStripContent(
            state = PreviewFixtures.DeviceName.display,
            onEditClick = {},
            onDraftChange = {},
            onConfirm = {},
            onCancel = {},
            modifier = Modifier.fillMaxWidth(),
        )
        if (hasPendingOutbound) {
            PendingOutboundBanner(
                state = PendingOutboundBannerState.Default(
                    summary = PendingFilesSummary(fileCount = 2, totalBytes = 10_485_760L),
                    dropFeedback = false,
                ),
                onCancel = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
                items(specs, key = { it.device.host }) { spec ->
                    PeerCardContent(
                        state = spec,
                        callbacks = previewCallbacks(),
                        modifier = Modifier.fillMaxWidth(),
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
    onRetryOutbound = {},
    onShowDetails = {},
    onOpenFiles = {},
    onClick = null,
)
