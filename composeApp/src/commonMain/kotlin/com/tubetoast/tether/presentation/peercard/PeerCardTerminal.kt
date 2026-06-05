package com.tubetoast.tether.presentation.peercard

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.PeerTransferState
import com.tubetoast.tether.transfer.TransferErrorReason
import com.tubetoast.tether.ui.designsystem.BodyText
import com.tubetoast.tether.ui.designsystem.Button
import com.tubetoast.tether.ui.designsystem.DismissCloseButton
import com.tubetoast.tether.ui.designsystem.LabelText
import com.tubetoast.tether.ui.designsystem.TitleText
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.preview.TransferPreviewFixtures
import com.tubetoast.tether.ui.theme.TetherTheme
import compose.icons.TablerIcons
import compose.icons.tablericons.CircleCheck

private val LeadingIconSize = 18.dp

@Composable
internal fun PeerCardSent(
    state: PeerTransferState.Sent,
    device: Device,
    callbacks: PeerCardCallbacks,
    modifier: Modifier = Modifier,
    isPaired: Boolean = false,
) {
    val peerName = device.name
    TerminalShell(
        peerName = peerName,
        statusCopy = sentCardCopy(state),
        showDetails = true,
        dismissDescription = "Dismiss sent notification to $peerName",
        onDismiss = callbacks.onDismiss,
        onShowDetails = callbacks.onShowDetails,
        leadingIcon = TablerIcons.CircleCheck,
        isPaired = isPaired,
        modifier = modifier,
    )
}

/**
 * @param deepLinkHint Platform-specific copy shown when the OS deep-link to the saved folder fails.
 *   Defaults to the Desktop JVM copy; per-platform wiring is deferred — see TODO below.
 * @param showDeepLinkHint Whether to render the hint. Defaults to false; set to true by the call
 *   site on deep-link failure once the OS deep-link mechanism is wired per platform.
 *   TODO(#195): per-platform OS deep-link attempt — if call fails, pass showDeepLinkHint = true
 *   so the platform-specific inline hint from UX brief §State 6 renders.
 */
@Composable
internal fun PeerCardReceived(
    state: PeerTransferState.Received,
    device: Device,
    callbacks: PeerCardCallbacks,
    modifier: Modifier = Modifier,
    isPaired: Boolean = false,
    deepLinkHint: String = "Open file manager → Downloads → Tether",
    showDeepLinkHint: Boolean = false,
) {
    val peerName = device.name

    PeerCardShell(modifier = modifier, isPaired = isPaired) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TitleText(text = peerName, modifier = Modifier.weight(1f))
            DismissCloseButton(
                onClick = callbacks.onDismiss,
                contentDescription = "Dismiss received notification from $peerName",
            )
        }

        BodyText(
            text = receivedCardCopy(state),
            modifier = if (state.partialReason == null) {
                Modifier
                    .clickable { callbacks.onOpenFiles() }
                    .semantics {
                        role = Role.Button
                        contentDescription = "Open files received from $peerName"
                    }
            } else {
                Modifier
            },
        )

        if (showDeepLinkHint) {
            LabelText(text = deepLinkHint)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                label = "Show details →",
                onClick = callbacks.onShowDetails,
                contentDescription = "Show transfer details for $peerName",
            )
        }
    }
}

@Composable
internal fun PeerCardCancelled(
    state: PeerTransferState.Cancelled,
    device: Device,
    callbacks: PeerCardCallbacks,
    modifier: Modifier = Modifier,
    isPaired: Boolean = false,
) {
    val peerName = device.name
    TerminalShell(
        peerName = peerName,
        statusCopy = cancelledCardCopy(state),
        showDetails = true,
        dismissDescription = "Dismiss cancelled transfer for $peerName",
        onDismiss = callbacks.onDismiss,
        onShowDetails = callbacks.onShowDetails,
        isPaired = isPaired,
        modifier = modifier,
    )
}

@Composable
internal fun PeerCardError(
    state: PeerTransferState.Error,
    device: Device,
    isOnline: Boolean,
    callbacks: PeerCardCallbacks,
    modifier: Modifier = Modifier,
    isPaired: Boolean = false,
) {
    val colors = TetherTheme.colors
    val peerName = device.name
    val retryEnabled = isOnline && state.reason != TransferErrorReason.ReceiverSuspended
    val retryDesc = if (retryEnabled) {
        "Retry sending to $peerName"
    } else {
        "Retry not available — $peerName is offline"
    }

    PeerCardShell(modifier = modifier, isPaired = isPaired) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TitleText(text = peerName, modifier = Modifier.weight(1f))
            DismissCloseButton(
                onClick = callbacks.onDismiss,
                contentDescription = "Dismiss error for $peerName",
            )
        }

        BodyText(text = errorCardCopy(state), color = colors.error)

        if (!isOnline && state.reason != TransferErrorReason.ReceiverSuspended) {
            LabelText(text = "$peerName is offline")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                label = "Show details →",
                onClick = callbacks.onShowDetails,
                contentDescription = "Show transfer details for $peerName",
            )
            if (state.reason != TransferErrorReason.ReceiverSuspended) {
                Button(
                    label = "Retry",
                    onClick = callbacks.onRetryOutbound,
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
    showDetails: Boolean,
    dismissDescription: String,
    onDismiss: () -> Unit,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    isPaired: Boolean = false,
) {
    val colors = TetherTheme.colors
    val spacing = TetherTheme.spacing

    PeerCardShell(modifier = modifier, isPaired = isPaired) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TitleText(text = peerName, modifier = Modifier.weight(1f))
            DismissCloseButton(
                onClick = onDismiss,
                contentDescription = dismissDescription,
            )
        }

        if (leadingIcon != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = rememberVectorPainter(leadingIcon),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.accent),
                    modifier = Modifier.size(LeadingIconSize),
                )
                Spacer(Modifier.width(spacing.sm))
                TitleText(text = statusCopy, modifier = Modifier.weight(1f))
            }
        } else {
            BodyText(text = statusCopy)
        }

        if (showDetails) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    label = "Show details →",
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
    onCancel = {},
    onDismiss = {},
    onRetryOutbound = {},
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
