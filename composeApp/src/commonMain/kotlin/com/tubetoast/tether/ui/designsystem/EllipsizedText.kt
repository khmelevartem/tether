package com.tubetoast.tether.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun EllipsizedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TetherTheme.typography.bodyMedium,
    contentDescription: String? = null,
) {
    val resolvedStyle = style.copy(
        color = if (style.color == Color.Unspecified) TetherTheme.colors.textPrimary else style.color,
    )
    val textMeasurer = rememberTextMeasurer()
    var availableWidth by remember { mutableStateOf(Int.MAX_VALUE) }

    val displayText = remember(text, availableWidth, resolvedStyle) {
        middleEllipsize(text, availableWidth) { candidate ->
            val result = textMeasurer.measure(candidate, resolvedStyle, maxLines = 1)
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
            style = resolvedStyle,
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

@Preview(name = "EllipsizedText — short")
@Composable
private fun PreviewEllipsizedTextShort(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        EllipsizedText(
            text = "photo.jpg",
            contentDescription = "Currently sending: photo.jpg",
        )
    }

@Preview(name = "EllipsizedText — long")
@Composable
private fun PreviewEllipsizedTextLong(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        EllipsizedText(text = "very_long_document_name_that_might_overflow_the_available_width.pdf")
    }
