package com.tubetoast.tether.presentation.peercard

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.ByteFormatting
import com.tubetoast.tether.transfer.PeerTransferState
import com.tubetoast.tether.ui.designsystem.Button
import com.tubetoast.tether.ui.designsystem.NumericText
import com.tubetoast.tether.ui.designsystem.ProgressBar
import com.tubetoast.tether.ui.designsystem.TitleText
import com.tubetoast.tether.ui.feature.CurrentFileLabel
import com.tubetoast.tether.ui.feature.SkipCountBadge
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.preview.TransferPreviewFixtures
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
internal fun PeerCardActiveOutbound(
    state: PeerTransferState.ActiveOutbound,
    device: Device,
    callbacks: PeerCardCallbacks,
    modifier: Modifier = Modifier,
    isPaired: Boolean = false,
) {
    val peerName = device.name
    val sending = state as? PeerTransferState.ActiveOutbound.Sending
    val progress = if (sending != null && sending.totalBytes != null && sending.totalBytes > 0) {
        (sending.sentBytes.toFloat() / sending.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    ActiveCardShell(
        peerName = peerName,
        titlePrefix = null,
        progress = progress,
        currentFile = sending?.currentFile.orEmpty(),
        sentBytes = sending?.sentBytes ?: 0L,
        totalBytes = state.totalBytes,
        bytesPerSec = sending?.bytesPerSec,
        isInbound = false,
        isPaired = isPaired,
        trailingContent = {
            val skipped = sending?.skippedCount ?: 0
            if (skipped > 0) {
                SkipCountBadge(count = skipped)
            }
        },
        cancelDescription = "Cancel transfer to $peerName",
        onCancel = callbacks.onCancel,
        onShowDetails = callbacks.onShowDetails,
        showDetailsDescription = "Show transfer details for $peerName",
        modifier = modifier,
    )
}

@Composable
internal fun PeerCardActiveInbound(
    state: PeerTransferState.ActiveInbound,
    device: Device,
    callbacks: PeerCardCallbacks,
    modifier: Modifier = Modifier,
    isPaired: Boolean = false,
) {
    val peerName = device.name
    val progress = if (state.totalBytes != null && state.totalBytes > 0) {
        (state.receivedBytes.toFloat() / state.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    ActiveCardShell(
        peerName = "From $peerName",
        titlePrefix = null,
        progress = progress,
        currentFile = state.currentFile,
        sentBytes = state.receivedBytes,
        totalBytes = state.totalBytes,
        bytesPerSec = state.bytesPerSec,
        isInbound = true,
        isPaired = isPaired,
        trailingContent = {},
        cancelDescription = "Cancel incoming transfer from $peerName",
        onCancel = callbacks.onCancel,
        onShowDetails = callbacks.onShowDetails,
        showDetailsDescription = "Show transfer details for $peerName",
        modifier = modifier,
    )
}

@Composable
private fun ActiveCardShell(
    peerName: String,
    titlePrefix: String?,
    progress: Float,
    currentFile: String,
    sentBytes: Long,
    totalBytes: Long?,
    bytesPerSec: Long?,
    isInbound: Boolean,
    isPaired: Boolean,
    trailingContent: @Composable () -> Unit,
    cancelDescription: String,
    onCancel: () -> Unit,
    onShowDetails: () -> Unit,
    showDetailsDescription: String,
    modifier: Modifier = Modifier,
) {
    val spacing = TetherTheme.spacing
    val colors = TetherTheme.colors

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "transferProgress",
    )

    PeerCardShell(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        isPaired = isPaired,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val displayName = if (titlePrefix != null) "$titlePrefix$peerName" else peerName
            TitleText(
                text = displayName,
                modifier = Modifier.weight(1f),
            )
            Button(
                label = "Cancel",
                onClick = onCancel,
                contentDescription = cancelDescription,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = spacing.xxl),
        ) {
            CurrentFileLabel(
                fileName = currentFile,
                contentDescription = if (isInbound) {
                    "Currently receiving: $currentFile"
                } else {
                    "Currently sending: $currentFile"
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(spacing.xs))

            TransferProgressBar(
                progress = animatedProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Transfer in progress" },
            )

            Spacer(Modifier.height(spacing.xs))

            Row(modifier = Modifier.fillMaxWidth()) {
                val sentFormatted = ByteFormatting.formatSize(sentBytes)
                val totalFormatted = totalBytes?.let { ByteFormatting.formatSize(it) }
                NumericText(
                    text = if (totalFormatted != null) "$sentFormatted of $totalFormatted" else sentFormatted,
                    color = colors.textMuted,
                    modifier = Modifier.weight(1f),
                )
                if (bytesPerSec != null) {
                    NumericText(
                        text = "${ByteFormatting.formatSize(bytesPerSec)}/s",
                        color = colors.textMuted,
                    )
                } else {
                    NumericText(
                        text = "Calculating…",
                        color = colors.textMuted,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            trailingContent()
            Button(
                label = "Show details →",
                onClick = onShowDetails,
                contentDescription = showDetailsDescription,
            )
        }
    }
}

@Composable
private fun TransferProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    ProgressBar(progress = progress, modifier = modifier)
}

@Preview(name = "PeerCardActiveOutbound — claimed")
@Composable
private fun PreviewActiveOutboundClaimed(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardActiveOutbound(
            state = TransferPreviewFixtures.activeOutboundClaimed,
            device = TransferPreviewFixtures.device,
            callbacks = previewCardCallbacks(),
        )
    }

@Preview(name = "PeerCardActiveOutbound — normal")
@Composable
private fun PreviewActiveOutbound(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardActiveOutbound(
            state = TransferPreviewFixtures.activeOutbound,
            device = TransferPreviewFixtures.device,
            callbacks = previewCardCallbacks(),
        )
    }

@Preview(name = "PeerCardActiveOutbound — with skips")
@Composable
private fun PreviewActiveOutboundWithSkips(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardActiveOutbound(
            state = TransferPreviewFixtures.activeOutboundWithSkips,
            device = TransferPreviewFixtures.device,
            callbacks = previewCardCallbacks(),
        )
    }

@Preview(name = "PeerCardActiveOutbound — calculating")
@Composable
private fun PreviewActiveOutboundCalculating(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardActiveOutbound(
            state = TransferPreviewFixtures.activeOutboundCalculating,
            device = TransferPreviewFixtures.device,
            callbacks = previewCardCallbacks(),
        )
    }

@Preview(name = "PeerCardActiveInbound")
@Composable
private fun PreviewActiveInbound(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardActiveInbound(
            state = TransferPreviewFixtures.activeInbound,
            device = TransferPreviewFixtures.device,
            callbacks = previewCardCallbacks(),
        )
    }

private fun previewCardCallbacks() = PeerCardCallbacks(
    onToggleExpand = {},
    onToggleAutoSend = {},
    onCancel = {},
    onDismiss = {},
    onRetryOutbound = {},
    onShowDetails = {},
    onOpenFiles = {},
)
