package com.tubetoast.tether.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun CurrentFileLabel(
    fileName: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val style = TetherTheme.typography.bodyMedium.copy(color = TetherTheme.colors.textPrimary)
    val textMeasurer = rememberTextMeasurer()
    var availableWidth by remember { mutableStateOf(Int.MAX_VALUE) }

    val displayText = remember(fileName, availableWidth, style) {
        middleEllipsize(fileName, availableWidth) { candidate ->
            val result = textMeasurer.measure(candidate, style, maxLines = 1)
            result.size.width <= availableWidth
        }
    }

    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .then(semanticsModifier)
            .onSizeChanged { availableWidth = it.width },
    ) {
        BasicText(
            text = displayText,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

internal fun middleEllipsize(text: String, availableWidth: Int, fits: (String) -> Boolean): String {
    if (text.isEmpty()) return "…"
    if (availableWidth == Int.MAX_VALUE) return text
    if (fits(text)) return text

    val ellipsis = "…"
    var lo = 0
    var hi = text.length / 2
    var result = ellipsis
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        val head = text.take(mid)
        val tail = text.takeLast(mid)
        val candidate = "$head$ellipsis$tail"
        if (fits(candidate)) {
            result = candidate
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return result
}

internal fun middleEllipsize(
    text: String,
    measurer: TextMeasurer,
    style: TextStyle,
    availableWidth: Int,
): String = middleEllipsize(text, availableWidth) { candidate ->
    measurer.measure(candidate, style, maxLines = 1).size.width <= availableWidth
}

@Preview(name = "CurrentFileLabel — short name")
@Composable
private fun PreviewCurrentFileLabelShort() {
    PreviewSurface {
        CurrentFileLabel(
            fileName = "photo.jpg",
            contentDescription = "Currently sending: photo.jpg",
        )
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
