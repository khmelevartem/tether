package com.tubetoast.tether.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.theme.TetherTheme

/**
 * Displays a file name with middle ellipsis — head and tail both remain visible
 * when the text overflows, rather than clipping only the tail.
 */
@Composable
fun CurrentFileLabel(
    fileName: String,
    modifier: Modifier = Modifier,
) {
    val style = TetherTheme.typography.bodyMedium.copy(color = TetherTheme.colors.textPrimary)
    val textMeasurer = rememberTextMeasurer()
    var availableWidth by remember { mutableStateOf(Int.MAX_VALUE) }

    val displayText = remember(fileName, availableWidth, style) {
        middleEllipsize(fileName, textMeasurer, style, availableWidth)
    }

    BasicText(
        text = displayText,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = modifier,
        onTextLayout = { result ->
            availableWidth = result.size.width
        },
    )
}

private fun middleEllipsize(
    text: String,
    measurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
    availableWidth: Int,
): String {
    if (availableWidth == Int.MAX_VALUE) return text
    val measured = measurer.measure(text, style, maxLines = 1)
    if (!measured.hasVisualOverflow) return text

    val ellipsis = "…"
    // Binary search on head length, keeping equal tail
    var lo = 0
    var hi = text.length / 2
    var result = ellipsis
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        val head = text.take(mid)
        val tail = text.takeLast(mid)
        val candidate = "$head$ellipsis$tail"
        val candidateMeasured = measurer.measure(candidate, style, maxLines = 1)
        if (candidateMeasured.size.width <= availableWidth) {
            result = candidate
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return result
}

@Preview(name = "CurrentFileLabel — short name")
@Composable
private fun PreviewCurrentFileLabelShort() {
    PreviewSurface {
        CurrentFileLabel(fileName = "photo.jpg")
    }
}

@Preview(name = "CurrentFileLabel — long name")
@Composable
private fun PreviewCurrentFileLabelLong() {
    PreviewSurface {
        CurrentFileLabel(fileName = "very_long_document_name_that_might_overflow_the_available_width.pdf")
    }
}

@Preview(name = "CurrentFileLabel — dark")
@Composable
private fun PreviewCurrentFileLabelDark() {
    PreviewSurface(darkTheme = true) {
        CurrentFileLabel(fileName = "very_long_document_name_that_might_overflow_the_available_width.pdf")
    }
}
