package com.tubetoast.tether.presentation.peercard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
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
import com.tubetoast.tether.presentation.transfer.PeerTransferState
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.TransferErrorReason
import com.tubetoast.tether.ui.components.BrandMark
import com.tubetoast.tether.ui.components.BrandMarkState
import com.tubetoast.tether.ui.components.DismissCloseButton
import com.tubetoast.tether.ui.components.RetryTextButton
import com.tubetoast.tether.ui.components.ShowDetailsButton
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.preview.TransferPreviewFixtures
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun PeerCardSent(
    state: PeerTransferState.Sent,
    device: Device,
    callbacks: PeerCardCallbacks,
    modifier: Modifier = Modifier,
) {
    val peerName = device.name
    TerminalShell(
        peerName = peerName,
        statusCopy = sentCardCopy(state),
        brandMarkState = BrandMarkState.Success,
        brandMarkContentDesc = "Transfer complete",
        showDetails = true,
        dismissDescription = "Dismiss sent notification to $peerName",
        onDismiss = callbacks.onDismiss,
        onShowDetails = callbacks.onShowDetails,
        modifier = modifier,
    )
}

@Composable
fun PeerCardReceived(
    state: PeerTransferState.Received,
    device: Device,
    callbacks: PeerCardCallbacks,
    modifier: Modifier = Modifier,
) {
    val colors = TetherTheme.colors
    val spacing = TetherTheme.spacing
    val typography = TetherTheme.typography
    val shapes = TetherTheme.shapes
    val peerName = device.name

    var showDeepLinkHint by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shapes.md)
            .background(colors.surfaceRaised)
            .border(spacing.borderWidth, colors.border, shapes.md)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText(
                text = peerName,
                style = typography.titleMedium.copy(color = colors.textPrimary),
                modifier = Modifier.weight(1f),
            )
            DismissCloseButton(
                onClick = callbacks.onDismiss,
                contentDescription = "Dismiss received notification from $peerName",
            )
        }

        BrandMark(
            state = BrandMarkState.Success,
            contentDescription = "Transfer complete",
        )

        BasicText(
            text = receivedCardCopy(state),
            style = typography.bodyMedium.copy(color = colors.textPrimary),
            modifier = if (state.partialReason == null) {
                Modifier
                    .clickable {
                        callbacks.onOpenFiles()
                        showDeepLinkHint = true
                    }.semantics {
                        role = Role.Button
                        contentDescription = "Open files received from $peerName"
                    }
            } else {
                Modifier
            },
        )

        if (showDeepLinkHint) {
            BasicText(
                text = "Open file manager → Downloads → Tether",
                style = typography.labelSmall.copy(color = colors.textMuted),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            ShowDetailsButton(
                onClick = callbacks.onShowDetails,
                contentDescription = "Show transfer details for $peerName",
            )
        }
    }
}

@Composable
fun PeerCardCancelled(
    state: PeerTransferState.Cancelled,
    device: Device,
    callbacks: PeerCardCallbacks,
    modifier: Modifier = Modifier,
) {
    val peerName = device.name
    TerminalShell(
        peerName = peerName,
        statusCopy = cancelledCardCopy(state),
        brandMarkState = BrandMarkState.Disconnected,
        brandMarkContentDesc = null,
        showDetails = true,
        dismissDescription = "Dismiss cancelled transfer for $peerName",
        onDismiss = callbacks.onDismiss,
        onShowDetails = callbacks.onShowDetails,
        modifier = modifier,
    )
}

@Composable
fun PeerCardError(
    state: PeerTransferState.Error,
    device: Device,
    isOnline: Boolean,
    callbacks: PeerCardCallbacks,
    modifier: Modifier = Modifier,
) {
    val colors = TetherTheme.colors
    val spacing = TetherTheme.spacing
    val typography = TetherTheme.typography
    val shapes = TetherTheme.shapes
    val peerName = device.name
    val retryEnabled = isOnline && state.reason != TransferErrorReason.ReceiverSuspended
    val retryDesc = if (retryEnabled) {
        "Retry sending to $peerName"
    } else {
        "Retry not available — $peerName is offline"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shapes.md)
            .background(colors.surfaceRaised)
            .border(spacing.borderWidth, colors.border, shapes.md)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText(
                text = peerName,
                style = typography.titleMedium.copy(color = colors.textPrimary),
                modifier = Modifier.weight(1f),
            )
            DismissCloseButton(
                onClick = callbacks.onDismiss,
                contentDescription = "Dismiss error for $peerName",
            )
        }

        BrandMark(
            state = BrandMarkState.Error(progress = if (state.sent > 0) 0.5f else 0f),
            contentDescription = "Transfer failed",
        )

        BasicText(
            text = errorCardCopy(state),
            style = typography.bodyMedium.copy(color = colors.error),
        )

        if (!isOnline && state.reason != TransferErrorReason.ReceiverSuspended) {
            BasicText(
                text = "$peerName is offline",
                style = typography.labelSmall.copy(color = colors.textMuted),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShowDetailsButton(
                onClick = callbacks.onShowDetails,
                contentDescription = "Show transfer details for $peerName",
            )
            if (state.reason != TransferErrorReason.ReceiverSuspended) {
                RetryTextButton(
                    onClick = callbacks.onRetry,
                    contentDescription = retryDesc,
                    enabled = retryEnabled,
                )
            }
        }
    }
}

@Composable
private fun TerminalShell(
    peerName: String,
    statusCopy: String,
    brandMarkState: BrandMarkState,
    brandMarkContentDesc: String?,
    showDetails: Boolean,
    dismissDescription: String,
    onDismiss: () -> Unit,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TetherTheme.colors
    val spacing = TetherTheme.spacing
    val typography = TetherTheme.typography
    val shapes = TetherTheme.shapes

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shapes.md)
            .background(colors.surfaceRaised)
            .border(spacing.borderWidth, colors.border, shapes.md)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText(
                text = peerName,
                style = typography.titleMedium.copy(color = colors.textPrimary),
                modifier = Modifier.weight(1f),
            )
            DismissCloseButton(
                onClick = onDismiss,
                contentDescription = dismissDescription,
            )
        }

        BrandMark(
            state = brandMarkState,
            contentDescription = brandMarkContentDesc,
        )

        BasicText(
            text = statusCopy,
            style = typography.bodyMedium.copy(color = colors.textPrimary),
        )

        if (showDetails) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                ShowDetailsButton(
                    onClick = onShowDetails,
                    contentDescription = "Show transfer details for $peerName",
                )
            }
        }
    }
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
)

@Preview(name = "PeerCardSent — full")
@Composable
private fun PreviewSentFull(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardSent(
            state = TransferPreviewFixtures.sentFull,
            device = TransferPreviewFixtures.device,
            callbacks = previewCallbacks(),
        )
    }

@Preview(name = "PeerCardSent — partial receiver cancelled")
@Composable
private fun PreviewSentPartialReceiverCancelled(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardSent(
            state = TransferPreviewFixtures.sentPartialReceiverCancelled,
            device = TransferPreviewFixtures.device,
            callbacks = previewCallbacks(),
        )
    }

@Preview(name = "PeerCardSent — partial connection lost")
@Composable
private fun PreviewSentPartialConnectionLost(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardSent(
            state = TransferPreviewFixtures.sentPartialConnectionLost,
            device = TransferPreviewFixtures.device,
            callbacks = previewCallbacks(),
        )
    }

@Preview(name = "PeerCardSent — partial files unreadable")
@Composable
private fun PreviewSentPartialUnreadable(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardSent(
            state = TransferPreviewFixtures.sentPartialUnreadable,
            device = TransferPreviewFixtures.device,
            callbacks = previewCallbacks(),
        )
    }

@Preview(name = "PeerCardReceived — full")
@Composable
private fun PreviewReceivedFull(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardReceived(
            state = TransferPreviewFixtures.receivedFull,
            device = TransferPreviewFixtures.device,
            callbacks = previewCallbacks(),
        )
    }

@Preview(name = "PeerCardReceived — partial sender cancelled")
@Composable
private fun PreviewReceivedPartialSenderCancelled(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardReceived(
            state = TransferPreviewFixtures.receivedPartialSenderCancelled,
            device = TransferPreviewFixtures.device,
            callbacks = previewCallbacks(),
        )
    }

@Preview(name = "PeerCardReceived — partial receiver cancelled")
@Composable
private fun PreviewReceivedPartialReceiverCancelled(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardReceived(
            state = TransferPreviewFixtures.receivedPartialReceiverCancelled,
            device = TransferPreviewFixtures.device,
            callbacks = previewCallbacks(),
        )
    }

@Preview(name = "PeerCardReceived — partial connection lost")
@Composable
private fun PreviewReceivedPartialConnectionLost(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardReceived(
            state = TransferPreviewFixtures.receivedPartialConnectionLost,
            device = TransferPreviewFixtures.device,
            callbacks = previewCallbacks(),
        )
    }

@Preview(name = "PeerCardCancelled — clean")
@Composable
private fun PreviewCancelledClean(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardCancelled(
            state = TransferPreviewFixtures.cancelledClean,
            device = TransferPreviewFixtures.device,
            callbacks = previewCallbacks(),
        )
    }

@Preview(name = "PeerCardCancelled — partial")
@Composable
private fun PreviewCancelledPartial(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardCancelled(
            state = TransferPreviewFixtures.cancelledPartial,
            device = TransferPreviewFixtures.device,
            callbacks = previewCallbacks(),
        )
    }

@Preview(name = "PeerCardError — network lost")
@Composable
private fun PreviewErrorNetworkLost(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardError(
            state = TransferPreviewFixtures.errorNetworkLost,
            device = TransferPreviewFixtures.device,
            isOnline = true,
            callbacks = previewCallbacks(),
        )
    }

@Preview(name = "PeerCardError — peer unreachable")
@Composable
private fun PreviewErrorPeerUnreachable(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardError(
            state = TransferPreviewFixtures.errorPeerUnreachable,
            device = TransferPreviewFixtures.device,
            isOnline = true,
            callbacks = previewCallbacks(),
        )
    }

@Preview(name = "PeerCardError — receiver write failed")
@Composable
private fun PreviewErrorReceiverWriteFailed(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardError(
            state = TransferPreviewFixtures.errorReceiverWriteFailed,
            device = TransferPreviewFixtures.device,
            isOnline = true,
            callbacks = previewCallbacks(),
        )
    }

@Preview(name = "PeerCardError — all files failed")
@Composable
private fun PreviewErrorAllFilesFailed(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardError(
            state = TransferPreviewFixtures.errorAllFilesFailed,
            device = TransferPreviewFixtures.device,
            isOnline = true,
            callbacks = previewCallbacks(),
        )
    }

@Preview(name = "PeerCardError — offline retry disabled")
@Composable
private fun PreviewErrorOfflineRetryDisabled(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardError(
            state = TransferPreviewFixtures.errorNetworkLost,
            device = TransferPreviewFixtures.device,
            isOnline = false,
            callbacks = previewCallbacks(),
        )
    }

@Preview(name = "PeerCardError — iOS suspension")
@Composable
private fun PreviewErrorIosSuspension(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardError(
            state = TransferPreviewFixtures.errorReceiverSuspended,
            device = TransferPreviewFixtures.device,
            isOnline = false,
            callbacks = previewCallbacks(),
        )
    }
