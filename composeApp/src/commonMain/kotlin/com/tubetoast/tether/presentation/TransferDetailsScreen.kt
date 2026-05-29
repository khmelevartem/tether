package com.tubetoast.tether.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.tubetoast.tether.presentation.transfer.PeerTransferState
import com.tubetoast.tether.presentation.transfer.PerFileRow
import com.tubetoast.tether.presentation.transfer.TransferDetailsComponent
import com.tubetoast.tether.presentation.transfer.aggregateStripCopy
import com.tubetoast.tether.presentation.transfer.detailsSubtitleCopy
import com.tubetoast.tether.transfer.PerFileStatus
import com.tubetoast.tether.ui.designsystem.BodyText
import com.tubetoast.tether.ui.designsystem.DismissCloseButton
import com.tubetoast.tether.ui.designsystem.LabelText
import com.tubetoast.tether.ui.designsystem.TetherButton
import com.tubetoast.tether.ui.designsystem.TitleText
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.preview.TransferPreviewFixtures
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun TransferDetailsScreen(component: TransferDetailsComponent) {
    val state by component.state.subscribeAsState()
    TransferDetailsContent(
        state = state,
        onBack = component::onBack,
        onCancelTransfer = component::onCancelTransfer,
        onCancelFile = component::onCancelFile,
        onRetryFile = component::onRetryFile,
        onRetryAll = component::onRetryAll,
    )
}

@Composable
fun TransferDetailsContent(
    state: PeerTransferState,
    onBack: () -> Unit,
    onCancelTransfer: () -> Unit,
    onCancelFile: (String) -> Unit,
    onRetryFile: (String) -> Unit,
    onRetryAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TetherTheme.colors
    val spacing = TetherTheme.spacing

    val perFile = perFileList(state)
    val isSenderSide = state is PeerTransferState.ActiveOutbound ||
        state is PeerTransferState.Sent ||
        state is PeerTransferState.Cancelled ||
        (state is PeerTransferState.Error)
    val isActive = state is PeerTransferState.ActiveOutbound || state is PeerTransferState.ActiveInbound
    val failedCount = perFile.count { it is PerFileStatus.Failed }
    val sentCount = perFile.count { it is PerFileStatus.Done }

    val firstItemFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (perFile.isNotEmpty()) {
            runCatching { firstItemFocusRequester.requestFocus() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        TopBar(
            peerName = state.peer.id,
            subtitle = detailsSubtitleCopy(state, state.peer.id),
            showCancel = isActive,
            onBack = onBack,
            onCancelTransfer = onCancelTransfer,
        )

        if (perFile.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg, vertical = spacing.sm),
            ) {
                AggregateStrip(
                    sentCount = sentCount,
                    totalCount = perFile.size,
                    failedCount = failedCount,
                    modifier = Modifier.weight(1f),
                )
                if (failedCount > 0 && isSenderSide) {
                    TetherButton(
                        label = "Retry",
                        onClick = onRetryAll,
                        contentDescription = "Retry all $failedCount failed files",
                    )
                }
            }
        }

        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            items(perFile, key = { it.name }) { fileStatus ->
                val isFirst = perFile.first() == fileStatus
                PerFileRow(
                    status = fileStatus,
                    isSenderSide = isSenderSide,
                    onCancelFile = onCancelFile,
                    onRetryFile = onRetryFile,
                    modifier = if (isFirst) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    peerName: String,
    subtitle: String,
    showCancel: Boolean,
    onBack: () -> Unit,
    onCancelTransfer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = TetherTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.sm, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        DismissCloseButton(
            onClick = onBack,
            contentDescription = "Back to device list",
        )
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = spacing.sm),
        ) {
            TitleText(text = peerName)
            LabelText(text = subtitle)
        }
        if (showCancel) {
            TetherButton(
                label = "Cancel",
                onClick = onCancelTransfer,
                contentDescription = "Cancel transfer to $peerName",
            )
        }
    }
}

@Composable
private fun AggregateStrip(
    sentCount: Int,
    totalCount: Int,
    failedCount: Int,
    modifier: Modifier = Modifier,
) {
    BodyText(
        text = aggregateStripCopy(sent = sentCount, total = totalCount, failed = failedCount),
        color = TetherTheme.colors.textMuted,
        modifier = modifier,
    )
}

private fun perFileList(state: PeerTransferState): List<PerFileStatus> = when (state) {
    is PeerTransferState.ActiveOutbound -> state.perFile
    is PeerTransferState.ActiveInbound -> state.perFile
    is PeerTransferState.Sent -> state.perFile
    is PeerTransferState.Received -> state.perFile
    is PeerTransferState.Cancelled -> state.perFile
    is PeerTransferState.Error -> state.perFile
    is PeerTransferState.Reconnecting -> perFileList(state.snapshotBeforeDrop)
    is PeerTransferState.Idle -> emptyList()
}

@Preview(name = "TransferDetailsScreen — in-progress")
@Composable
private fun PreviewInProgress(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        TransferDetailsContent(
            state = TransferPreviewFixtures.activeOutboundWithPerFile,
            onBack = {},
            onCancelTransfer = {},
            onCancelFile = {},
            onRetryFile = {},
            onRetryAll = {},
        )
    }

@Preview(name = "TransferDetailsScreen — all success terminal")
@Composable
private fun PreviewAllSuccess(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        TransferDetailsContent(
            state = TransferPreviewFixtures.sentPartialWithPerFile.copy(
                sent = 4,
                total = 4,
                perFile = TransferPreviewFixtures.perFileListInProgress.map {
                    if (it is PerFileStatus.InProgress || it is PerFileStatus.Queued) {
                        PerFileStatus.Done(it.name, it.size)
                    } else {
                        it
                    }
                },
                partialReason = null,
            ),
            onBack = {},
            onCancelTransfer = {},
            onCancelFile = {},
            onRetryFile = {},
            onRetryAll = {},
        )
    }

@Preview(name = "TransferDetailsScreen — partial with failures")
@Composable
private fun PreviewPartial(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        TransferDetailsContent(
            state = TransferPreviewFixtures.sentPartialWithPerFile,
            onBack = {},
            onCancelTransfer = {},
            onCancelFile = {},
            onRetryFile = {},
            onRetryAll = {},
        )
    }

@Preview(name = "TransferDetailsScreen — all failed with retry")
@Composable
private fun PreviewAllFailed(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        TransferDetailsContent(
            state = TransferPreviewFixtures.errorWithPerFile,
            onBack = {},
            onCancelTransfer = {},
            onCancelFile = {},
            onRetryFile = {},
            onRetryAll = {},
        )
    }
