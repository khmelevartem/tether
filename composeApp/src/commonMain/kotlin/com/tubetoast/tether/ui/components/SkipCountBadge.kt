package com.tubetoast.tether.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.tubetoast.tether.ui.preview.LightDarkPreview
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun SkipCountBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    if (count == 0) return

    val colors = TetherTheme.colors
    val spacing = TetherTheme.spacing
    val shapes = TetherTheme.shapes
    val noun = if (count == 1) "file" else "files"

    Box(
        modifier = modifier
            .semantics { contentDescription = "$count $noun skipped so far" }
            .clip(shapes.sm)
            .background(colors.surfaceRaised)
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
    ) {
        BasicText(
            text = "$count $noun skipped",
            style = TetherTheme.typography.labelSmall.copy(color = colors.textMuted),
        )
    }
}

@Preview(name = "SkipCountBadge — count = 1 (singular)")
@Composable
private fun PreviewSkipCountBadgeSingular() = LightDarkPreview {
    SkipCountBadge(count = 1)
}

@Preview(name = "SkipCountBadge — count > 0")
@Composable
private fun PreviewSkipCountBadgeNonZero() = LightDarkPreview {
    SkipCountBadge(count = 3)
}

@Preview(name = "SkipCountBadge — count = 0 (emits nothing)")
@Composable
private fun PreviewSkipCountBadgeZero() {
    PreviewSurface {
        SkipCountBadge(count = 0)
    }
}
