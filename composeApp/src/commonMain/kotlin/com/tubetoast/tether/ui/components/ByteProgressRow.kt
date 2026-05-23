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
    calculatingPlaceholder: String = "Calculating…",
) {
    val text = buildProgressText(sentBytes, totalBytes, bytesPerSecond, calculatingPlaceholder)
    BasicText(
        text = text,
        style = TetherTheme.typography.numeric.copy(color = TetherTheme.colors.textPrimary),
        modifier = modifier,
    )
}

private fun buildProgressText(
    sentBytes: Long,
    totalBytes: Long?,
    bytesPerSecond: Long?,
    calculatingPlaceholder: String,
): String {
    val sent = formatBytes(sentBytes)
    return buildString {
        if (totalBytes != null) {
            append("$sent / ${formatBytes(totalBytes)}")
        } else {
            append(sent)
        }
        append(" · ")
        if (bytesPerSecond != null) {
            append("${formatBytes(bytesPerSecond)}/s")
        } else {
            append(calculatingPlaceholder)
        }
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "${formatOneDecimal(bytes / 1_073_741_824.0)} GB"
    bytes >= 1_048_576L -> "${formatOneDecimal(bytes / 1_048_576.0)} MB"
    bytes >= 1_024L -> "${formatOneDecimal(bytes / 1_024.0)} KB"
    else -> "$bytes B"
}

private fun formatOneDecimal(value: Double): String {
    val rounded = kotlin.math.round(value * 10).toInt()
    val whole = rounded / 10
    val frac = rounded % 10
    return "$whole.$frac"
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

@Preview(name = "ByteProgressRow — no total, calculating")
@Composable
private fun PreviewByteProgressRowCalculating() {
    PreviewSurface {
        ByteProgressRow(
            sentBytes = 52_428_800L,
            totalBytes = null,
            bytesPerSecond = null,
        )
    }
}

@Preview(name = "ByteProgressRow — with total, calculating speed")
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
