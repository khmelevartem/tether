package com.tubetoast.tether.presentation.peercard

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.tubetoast.tether.presentation.transfer.PeerTransferState
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.ui.components.ByteProgressRow
import com.tubetoast.tether.ui.components.CancelTextButton
import com.tubetoast.tether.ui.components.CurrentFileLabel
import com.tubetoast.tether.ui.components.ShowDetailsButton
import com.tubetoast.tether.ui.components.SkipCountBadge
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.preview.TransferPreviewFixtures
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun PeerCardActiveOutbound(
    state: PeerTransferState.ActiveOutbound,
    device: Device,
    callbacks: PeerCardCallbacks,
    modifier: Modifier = Modifier,
) {
    val peerName = device.name
    val progress = if (state.totalBytes != null && state.totalBytes > 0) {
        (state.sentBytes.toFloat() / state.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    ActiveCardShell(
        peerName = peerName,
        titlePrefix = null,
        progress = progress,
        currentFile = state.currentFile,
        sentBytes = state.sentBytes,
        totalBytes = state.totalBytes,
        bytesPerSec = state.bytesPerSec,
        trailingContent = {
            if (state.skippedCount > 0) {
                SkipCountBadge(count = state.skippedCount)
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
fun PeerCardActiveInbound(
    state: PeerTransferState.ActiveInbound,
    device: Device,
    callbacks: PeerCardCallbacks,
    modifier: Modifier = Modifier,
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
    trailingContent: @Composable () -> Unit,
    cancelDescription: String,
    onCancel: () -> Unit,
    onShowDetails: () -> Unit,
    showDetailsDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = TetherTheme.colors
    val spacing = TetherTheme.spacing
    val typography = TetherTheme.typography
    val shapes = TetherTheme.shapes

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "transferProgress",
    )

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
            val displayName = if (titlePrefix != null) "$titlePrefix$peerName" else peerName
            BasicText(
                text = displayName,
                style = typography.titleMedium.copy(color = colors.textPrimary),
                modifier = Modifier.weight(1f),
            )
            CancelTextButton(
                onClick = onCancel,
                contentDescription = cancelDescription,
            )
        }

        TransferProgressBar(
            progress = animatedProgress,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Transfer in progress" },
        )

        CurrentFileLabel(
            fileName = currentFile,
            contentDescription = "Currently sending: $currentFile",
            modifier = Modifier.fillMaxWidth(),
        )

        ByteProgressRow(
            sentBytes = sentBytes,
            totalBytes = totalBytes,
            bytesPerSecond = bytesPerSec,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            trailingContent()
            ShowDetailsButton(
                onClick = onShowDetails,
                contentDescription = showDetailsDescription,
            )
        }
    }
}

@Composable
fun TransferProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val colors = TetherTheme.colors
    val shapes = TetherTheme.shapes

    Box(
        modifier = modifier
            .height(4.dp)
            .clip(shapes.sm)
            .background(colors.border),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(shapes.sm)
                .background(colors.accent),
        )
    }
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
    onShowAutoSendInfo = {},
    onCancel = {},
    onDismiss = {},
    onRetry = {},
    onShowDetails = {},
    onOpenFiles = {},
)
