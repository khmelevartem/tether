package com.tubetoast.tether.ui.feature

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.tubetoast.tether.transfer.ByteFormatting
import com.tubetoast.tether.ui.designsystem.NumericText
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes

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
    NumericText(
        text = text,
        modifier = modifier.then(semanticsModifier),
    )
}

private fun buildProgressText(
    sentBytes: Long,
    totalBytes: Long?,
    bytesPerSecond: Long?,
    calculatingPlaceholder: String,
): String {
    val sent = ByteFormatting.formatSize(sentBytes)
    return buildString {
        if (totalBytes != null) {
            append("$sent / ${ByteFormatting.formatSize(totalBytes)}")
        } else {
            append(sent)
        }
        append(" · ")
        if (bytesPerSecond != null) {
            append("${ByteFormatting.formatSize(bytesPerSecond)}/s")
        } else {
            append(calculatingPlaceholder)
        }
    }
}

@Preview(name = "ByteProgressRow — with total and rate")
@Composable
private fun PreviewByteProgressRowFull(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        ByteProgressRow(
            sentBytes = 52_428_800L,
            totalBytes = 104_857_600L,
            bytesPerSecond = 5_242_880L,
            contentDescription = "Transfer speed: 5 MB/s",
        )
    }

@Preview(name = "ByteProgressRow — no total, calculating")
@Composable
private fun PreviewByteProgressRowCalculating(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        ByteProgressRow(
            sentBytes = 52_428_800L,
            totalBytes = null,
            bytesPerSecond = null,
        )
    }

@Preview(name = "ByteProgressRow — with total, calculating speed")
@Composable
private fun PreviewByteProgressRowNoRate(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        ByteProgressRow(
            sentBytes = 52_428_800L,
            totalBytes = 104_857_600L,
            bytesPerSecond = null,
        )
    }
