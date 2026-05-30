package com.tubetoast.tether.ui.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.tubetoast.tether.ui.designsystem.LabelText
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
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
        LabelText(text = "$count $noun skipped")
    }
}

@Preview(name = "SkipCountBadge — count = 1 (singular)")
@Composable
private fun PreviewSkipCountBadgeSingular(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) { SkipCountBadge(count = 1) }

@Preview(name = "SkipCountBadge — count > 0")
@Composable
private fun PreviewSkipCountBadgeNonZero(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) { SkipCountBadge(count = 3) }
