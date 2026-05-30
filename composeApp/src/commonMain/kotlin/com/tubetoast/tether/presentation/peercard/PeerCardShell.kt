package com.tubetoast.tether.presentation.peercard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.tubetoast.tether.ui.theme.TetherTheme

/**
 * Shared card decoration for all PeerCard variants: surface tier, border, rounded corners.
 *
 * @param contentPadding Padding applied inside the card Column. Defaults to the standard
 *   horizontal + vertical inset used by most variants. Pass [PaddingValues] with zero values
 *   when the caller needs per-row padding control (e.g. PeerCardIdle).
 * @param verticalArrangement Spacing between child elements inside the column.
 * @param horizontalAlignment Horizontal alignment of children. Defaults to [Alignment.Start].
 */
@Composable
internal fun PeerCardShell(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = TetherTheme.spacing.lg,
        vertical = TetherTheme.spacing.md,
    ),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(TetherTheme.spacing.sm),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = TetherTheme.colors
    val spacing = TetherTheme.spacing
    val shapes = TetherTheme.shapes

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shapes.md)
            .background(colors.surfaceRaised)
            .border(spacing.borderWidth, colors.border, shapes.md)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}
