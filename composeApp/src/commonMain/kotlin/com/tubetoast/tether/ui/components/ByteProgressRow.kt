package com.tubetoast.tether.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.tubetoast.tether.ui.preview.LightDarkPreview
import com.tubetoast.tether.ui.theme.TetherTheme
import com.tubetoast.tether.util.formatBytes

@Composable
fun ByteProgressRow(
    sentBytes: Long,
    totalBytes: Long?,
    bytesPerSecond: Long?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    calculatingPlaceholder: String = "Calculating…",
) {
    val text = buildProgressText(sentBytes, totalBytes, bytesPerSecond, calculatingPlaceholder)
    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    BasicText(
        text = text,
        style = TetherTheme.typography.numeric.copy(color = TetherTheme.colors.textPrimary),
        modifier = modifier.then(semanticsModifier),
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

@Preview(name = "ByteProgressRow — with total and rate")
@Composable
private fun PreviewByteProgressRowFull() = LightDarkPreview {
    ByteProgressRow(
        sentBytes = 52_428_800L,
        totalBytes = 104_857_600L,
        bytesPerSecond = 5_242_880L,
        contentDescription = "Transfer speed: 5 MB/s",
    )
}

@Preview(name = "ByteProgressRow — no total, calculating")
@Composable
private fun PreviewByteProgressRowCalculating() = LightDarkPreview {
    ByteProgressRow(
        sentBytes = 52_428_800L,
        totalBytes = null,
        bytesPerSecond = null,
    )
}

@Preview(name = "ByteProgressRow — with total, calculating speed")
@Composable
private fun PreviewByteProgressRowNoRate() = LightDarkPreview {
    ByteProgressRow(
        sentBytes = 52_428_800L,
        totalBytes = 104_857_600L,
        bytesPerSecond = null,
    )
}
