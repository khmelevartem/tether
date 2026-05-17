package com.tubetoast.tether.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun CurrentFileLabel(
    fileName: String,
    modifier: Modifier = Modifier,
) {
    val style = TetherTheme.typography.bodyMedium.copy(color = TetherTheme.colors.textMuted)
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier.semantics { contentDescription = fileName }) {
        val text = remember(fileName, constraints.maxWidth, style) {
            middleEllipsis(fileName, style, measurer, constraints)
        }
        BasicText(text = text, style = style, maxLines = 1)
    }
}

private fun middleEllipsis(
    text: String,
    style: TextStyle,
    measurer: androidx.compose.ui.text.TextMeasurer,
    constraints: Constraints,
): String {
    if (text.isEmpty()) return text
    val fullWidth = measurer.measure(text, style).size.width
    if (fullWidth <= constraints.maxWidth) return text

    val ellipsis = "…"
    val ellipsisWidth = measurer.measure(ellipsis, style).size.width
    val available = constraints.maxWidth - ellipsisWidth
    if (available <= 0) return ellipsis

    val halfAvailable = available / 2
    var keepStart = text.length / 2
    while (keepStart > 0 && measurer.measure(text.take(keepStart), style).size.width > halfAvailable) {
        keepStart--
    }
    var keepEnd = text.length / 2
    while (keepEnd > 0 && measurer.measure(text.takeLast(keepEnd), style).size.width > halfAvailable) {
        keepEnd--
    }
    return "${text.take(keepStart)}$ellipsis${text.takeLast(keepEnd)}"
}
