package com.tubetoast.tether.presentation.banners

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.tubetoast.tether.presentation.PendingFilesSummary
import com.tubetoast.tether.transfer.ByteFormatting
import com.tubetoast.tether.ui.designsystem.Banner
import com.tubetoast.tether.ui.designsystem.BannerSeverity
import com.tubetoast.tether.ui.designsystem.TetherButton
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes

@Composable
fun PendingOutboundBanner(
    summary: PendingFilesSummary,
    dropFeedback: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val noun = if (summary.fileCount == 1) "file" else "files"
    val sizeLabel = ByteFormatting.formatSize(summary.totalBytes)
    val text = "Ready to send ${summary.fileCount} $noun ($sizeLabel). Pick a device below."

    Banner(
        text = text,
        severity = if (dropFeedback) BannerSeverity.Error else BannerSeverity.Info,
        modifier = modifier,
    ) {
        TetherButton(
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
            summary = PendingFilesSummary(1, 5_242_880L),
            dropFeedback = false,
            onCancel = {},
        )
    }

@Preview(name = "PendingOutboundBanner — multiple files")
@Composable
private fun PreviewMultipleFiles(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PendingOutboundBanner(
            summary = PendingFilesSummary(12, 524_288_000L),
            dropFeedback = false,
            onCancel = {},
        )
    }

@Preview(name = "PendingOutboundBanner — drop rejected flash")
@Composable
private fun PreviewDropRejected(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PendingOutboundBanner(
            summary = PendingFilesSummary(5, 104_857_600L),
            dropFeedback = true,
            onCancel = {},
        )
    }
