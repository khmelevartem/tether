package com.tubetoast.tether.presentation.transfer

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.tubetoast.tether.transfer.ByteFormatting
import com.tubetoast.tether.transfer.FailureReason
import com.tubetoast.tether.transfer.PerFileStatus
import com.tubetoast.tether.ui.designsystem.BodyText
import com.tubetoast.tether.ui.designsystem.LabelText
import com.tubetoast.tether.ui.designsystem.NumericText
import com.tubetoast.tether.ui.designsystem.ProgressBar
import com.tubetoast.tether.ui.designsystem.RowCancelButton
import com.tubetoast.tether.ui.feature.PeerIdentityAccent
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.preview.TransferPreviewFixtures
import com.tubetoast.tether.ui.theme.TetherTheme
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertCircle
import compose.icons.tablericons.Check
import compose.icons.tablericons.Clock

private val StatusIconSize = 20.dp
private val TrailingSlotSize = 28.dp
private val ProgressBarHeight = 3.dp
private val RowHeight = 56.dp

@Composable
fun PerFileRow(
    status: PerFileStatus,
    isSenderSide: Boolean,
    onCancelFile: (String) -> Unit,
    onRetryFile: (String) -> Unit,
    modifier: Modifier = Modifier,
    peerName: String = "",
    isOnline: Boolean = true,
    identityColor: Color = Color.Unspecified,
) {
    val colors = TetherTheme.colors
    val spacing = TetherTheme.spacing

    val rowModifier = if (status is PerFileStatus.Failed && isSenderSide) {
        val semanticLabel = failedRowSemanticLabel(status, peerName, isOnline)
        val peerOffline = status.reason is FailureReason.PeerUnreachable && !isOnline
        if (peerOffline) {
            modifier
                .fillMaxWidth()
                .height(RowHeight)
                .semantics {
                    role = Role.Button
                    contentDescription = semanticLabel
                }
        } else {
            modifier
                .fillMaxWidth()
                .height(RowHeight)
                .clickable(onClick = { onRetryFile(status.name) })
                .semantics {
                    role = Role.Button
                    contentDescription = semanticLabel
                }
        }
    } else {
        modifier
            .fillMaxWidth()
            .height(RowHeight)
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (identityColor != Color.Unspecified) {
            PeerIdentityAccent(identityColor = identityColor, width = 3.dp)
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                BodyText(text = status.name, maxLines = 1)
                when (status) {
                    is PerFileStatus.InProgress -> ProgressBar(
                        progress = if (status.size != null && status.size > 0) {
                            (status.bytesDone.toFloat() / status.size.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        },
                        height = ProgressBarHeight,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = spacing.xs),
                    )
                    is PerFileStatus.Failed -> LabelText(
                        text = failedRowHelperText(status),
                        color = colors.error,
                        modifier = Modifier.padding(top = spacing.xs),
                    )
                    else -> Unit
                }
            }

            status.size?.let { bytes ->
                NumericText(
                    text = ByteFormatting.formatSize(bytes),
                    color = colors.textMuted,
                )
            }

            TrailingSlot {
                when (status) {
                    is PerFileStatus.Queued -> {
                        if (isSenderSide) {
                            RowCancelButton(
                                onClick = { onCancelFile(status.name) },
                                contentDescription = "Cancel sending ${status.name}",
                            )
                        } else {
                            StatusIcon(
                                icon = TablerIcons.Clock,
                                tint = colors.textMuted,
                                contentDescription = "Queued",
                            )
                        }
                    }
                    is PerFileStatus.InProgress -> {
                        if (isSenderSide) {
                            RowCancelButton(
                                onClick = { onCancelFile(status.name) },
                                contentDescription = "Cancel sending ${status.name}",
                            )
                        }
                    }
                    is PerFileStatus.Done -> StatusIcon(
                        icon = TablerIcons.Check,
                        tint = colors.accent,
                        contentDescription = "Sent",
                    )
                    is PerFileStatus.Failed -> StatusIcon(
                        icon = TablerIcons.AlertCircle,
                        tint = colors.error,
                        contentDescription = "Failed",
                    )
                }
            }
        }
    }
}

@Composable
private fun TrailingSlot(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(TrailingSlotSize),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
private fun StatusIcon(
    icon: ImageVector,
    tint: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(StatusIconSize),
    )
}

private fun failedRowSemanticLabel(
    status: PerFileStatus.Failed,
    peerName: String,
    isOnline: Boolean,
): String {
    val name = status.name
    return when {
        status.cancelledByUser || status.reason is FailureReason.CancelledByUser ->
            "$name, cancelled by you. Activate to retry."
        status.reason is FailureReason.TransferCancelled ->
            "$name, transfer cancelled. Activate to retry."
        status.reason is FailureReason.PeerUnreachable && !isOnline ->
            "$name, not sent. Retry unavailable — $peerName is offline."
        else ->
            "$name, not sent. Activate to retry."
    }
}

private fun failedRowHelperText(status: PerFileStatus.Failed): String =
    when {
        status.cancelledByUser -> "Cancelled by you"
        status.reason is FailureReason.CancelledByUser -> "Cancelled by you"
        status.reason is FailureReason.TransferCancelled -> "Transfer cancelled"
        status.reason is FailureReason.PeerUnreachable -> "Peer is offline."
        status.reason is FailureReason.Unreadable -> "Couldn't read."
        status.reason is FailureReason.NetworkLost -> "Connection lost."
        status.reason is FailureReason.ReceiverWriteFailed -> "Couldn't save on receiver."
        status.reason is FailureReason.ReceiverSuspended -> "Receiver was suspended."
        else -> "Failed"
    }

@Preview(name = "PerFileRow — Queued sender")
@Composable
private fun PreviewQueued(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PerFileRow(
            status = TransferPreviewFixtures.perFileQueued,
            isSenderSide = true,
            onCancelFile = {},
            onRetryFile = {},
        )
    }

@Preview(name = "PerFileRow — InProgress sender")
@Composable
private fun PreviewInProgress(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PerFileRow(
            status = TransferPreviewFixtures.perFileInProgress,
            isSenderSide = true,
            onCancelFile = {},
            onRetryFile = {},
        )
    }

@Preview(name = "PerFileRow — Done")
@Composable
private fun PreviewDone(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PerFileRow(
            status = TransferPreviewFixtures.perFileDone,
            isSenderSide = true,
            onCancelFile = {},
            onRetryFile = {},
        )
    }

@Preview(name = "PerFileRow — Failed retryable sender")
@Composable
private fun PreviewFailedRetryable(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PerFileRow(
            status = TransferPreviewFixtures.perFileFailedRetryable,
            isSenderSide = true,
            onCancelFile = {},
            onRetryFile = {},
        )
    }

@Preview(name = "PerFileRow — Failed cancelled by user sender")
@Composable
private fun PreviewFailedCancelledByUser(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PerFileRow(
            status = TransferPreviewFixtures.perFileFailedCancelledByUser,
            isSenderSide = true,
            onCancelFile = {},
            onRetryFile = {},
        )
    }

@Preview(name = "PerFileRow — Failed transfer cancelled sender")
@Composable
private fun PreviewFailedTransferCancelled(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PerFileRow(
            status = TransferPreviewFixtures.perFileFailedTransferCancelled,
            isSenderSide = true,
            onCancelFile = {},
            onRetryFile = {},
        )
    }
