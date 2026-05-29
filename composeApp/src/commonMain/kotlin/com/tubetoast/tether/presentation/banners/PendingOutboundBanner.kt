package com.tubetoast.tether.presentation.banners

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.tubetoast.tether.presentation.PendingFilesSummary
import com.tubetoast.tether.transfer.ByteFormatting
import com.tubetoast.tether.ui.components.BodyText
import com.tubetoast.tether.ui.components.TetherButton
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun PendingOutboundBanner(
    summary: PendingFilesSummary,
    dropFeedback: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TetherTheme.colors
    val spacing = TetherTheme.spacing
    val backgroundColor = if (dropFeedback) colors.error.copy(alpha = 0.12f) else colors.accent.copy(alpha = 0.10f)
    val noun = if (summary.fileCount == 1) "file" else "files"
    val sizeLabel = ByteFormatting.formatSize(summary.totalBytes)
    val text = "Ready to send ${summary.fileCount} $noun ($sizeLabel). Pick a device below."

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = spacing.lg, vertical = spacing.sm)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BodyText(
            text = text,
            modifier = Modifier.weight(1f),
        )
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
