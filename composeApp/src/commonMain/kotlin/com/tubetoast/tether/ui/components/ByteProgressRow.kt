package com.tubetoast.tether.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun ByteProgressRow(
    sentBytes: Long,
    totalBytes: Long?,
    bytesPerSecond: Long?,
    modifier: Modifier = Modifier,
) {
    val text = buildProgressText(sentBytes, totalBytes, bytesPerSecond)
    BasicText(
        text = text,
        style = TetherTheme.typography.numeric.copy(color = TetherTheme.colors.textPrimary),
        modifier = modifier,
    )
}

private fun buildProgressText(sentBytes: Long, totalBytes: Long?, bytesPerSecond: Long?): String {
    val sent = formatBytes(sentBytes)
    return buildString {
        if (totalBytes != null) {
            append("$sent / ${formatBytes(totalBytes)}")
        } else {
            append(sent)
        }
        if (bytesPerSecond != null) {
            append(" · ${formatBytes(bytesPerSecond)}/s")
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L -> "%.1f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}

@Preview(name = "ByteProgressRow — with total and rate")
@Composable
private fun PreviewByteProgressRowFull() {
    PreviewSurface {
        ByteProgressRow(
            sentBytes = 52_428_800L,
            totalBytes = 104_857_600L,
            bytesPerSecond = 5_242_880L,
        )
    }
}

@Preview(name = "ByteProgressRow — no total")
@Composable
private fun PreviewByteProgressRowNoTotal() {
    PreviewSurface {
        ByteProgressRow(
            sentBytes = 52_428_800L,
            totalBytes = null,
            bytesPerSecond = null,
        )
    }
}

@Preview(name = "ByteProgressRow — with total, no rate")
@Composable
private fun PreviewByteProgressRowNoRate() {
    PreviewSurface {
        ByteProgressRow(
            sentBytes = 52_428_800L,
            totalBytes = 104_857_600L,
            bytesPerSecond = null,
        )
    }
}

@Preview(name = "ByteProgressRow — dark")
@Composable
private fun PreviewByteProgressRowDark() {
    PreviewSurface(darkTheme = true) {
        ByteProgressRow(
            sentBytes = 52_428_800L,
            totalBytes = 104_857_600L,
            bytesPerSecond = 5_242_880L,
        )
    }
}
