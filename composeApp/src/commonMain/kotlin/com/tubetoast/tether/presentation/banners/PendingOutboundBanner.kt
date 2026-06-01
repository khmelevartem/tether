package com.tubetoast.tether.presentation.banners

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.tubetoast.tether.transfer.ByteFormatting
import com.tubetoast.tether.transfer.PendingFilesSummary
import com.tubetoast.tether.ui.designsystem.Banner
import com.tubetoast.tether.ui.designsystem.BannerSeverity
import com.tubetoast.tether.ui.designsystem.Button
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes

@Composable
fun PendingOutboundBanner(
    state: PendingBannerState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        PendingBannerState.Hidden -> Unit
        is PendingBannerState.Default -> DefaultBanner(state.summary, state.dropFeedback, onCancel, modifier)
        is PendingBannerState.BusyPeer -> BusyPeerBanner(
            state.summary,
            state.peerName,
            state.announcementTick,
            onCancel,
            modifier,
        )
        is PendingBannerState.TerminalDisplay -> TerminalDisplayBanner(
            state.peerName,
            state.announcementTick,
            onCancel,
            modifier,
        )
    }
}

@Composable
private fun DefaultBanner(
    summary: PendingFilesSummary,
    dropFeedback: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val noun = if (summary.fileCount == 1) "file" else "files"
    val sizeLabel = ByteFormatting.formatSize(summary.totalBytes)
    Banner(
        text = "Ready to send ${summary.fileCount} $noun ($sizeLabel). Pick a device below.",
        severity = if (dropFeedback) BannerSeverity.Error else BannerSeverity.Info,
        modifier = modifier,
    ) {
        Button(
            label = "Cancel",
            onClick = onCancel,
            contentDescription = "Cancel pending transfer",
        )
    }
}

@Composable
private fun BusyPeerBanner(
    summary: PendingFilesSummary,
    peerName: String,
    announcementTick: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val noun = if (summary.fileCount == 1) "file" else "files"
    val sizeLabel = ByteFormatting.formatSize(summary.totalBytes)
    Banner(
        text = "$peerName is busy with another transfer. Your ${summary.fileCount} $noun ($sizeLabel) are still" +
            " ready — tap $peerName again when it's done, or pick a different device.",
        severity = BannerSeverity.Info,
        modifier = modifier.semantics { stateDescription = announcementTick.toString() },
    ) {
        Button(
            label = "Cancel",
            onClick = onCancel,
            contentDescription = "Cancel pending transfer",
        )
    }
}

@Composable
private fun TerminalDisplayBanner(
    peerName: String,
    announcementTick: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Banner(
        text = "$peerName's last transfer is still showing. Tap × on $peerName's card to dismiss it," +
            " then tap $peerName again — or pick a different device.",
        severity = BannerSeverity.Info,
        modifier = modifier.semantics { stateDescription = announcementTick.toString() },
    ) {
        Button(
            label = "Cancel",
            onClick = onCancel,
            contentDescription = "Cancel pending transfer",
        )
    }
}

@Preview(name = "PendingOutboundBanner — single file")
@Composable
private fun PreviewSingleFile(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PendingOutboundBanner(
            state = PendingBannerState.Default(PendingFilesSummary(1, 5_242_880L), false),
            onCancel = {},
        )
    }

@Preview(name = "PendingOutboundBanner — multiple files")
@Composable
private fun PreviewMultipleFiles(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PendingOutboundBanner(
            state = PendingBannerState.Default(PendingFilesSummary(12, 524_288_000L), false),
            onCancel = {},
        )
    }

@Preview(name = "PendingOutboundBanner — drop rejected flash")
@Composable
private fun PreviewDropRejected(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PendingOutboundBanner(
            state = PendingBannerState.Default(PendingFilesSummary(5, 104_857_600L), true),
            onCancel = {},
        )
    }

@Preview(name = "PendingOutboundBanner — busy peer")
@Composable
private fun PreviewBusyPeer(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PendingOutboundBanner(
            state = PendingBannerState.BusyPeer(PendingFilesSummary(3, 15_728_640L), "Alice's Mac", 1),
            onCancel = {},
        )
    }

@Preview(name = "PendingOutboundBanner — terminal display")
@Composable
private fun PreviewTerminalDisplay(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PendingOutboundBanner(
            state = PendingBannerState.TerminalDisplay("Alice's Mac", 1),
            onCancel = {},
        )
    }
