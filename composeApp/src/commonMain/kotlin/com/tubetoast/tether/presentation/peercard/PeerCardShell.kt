package com.tubetoast.tether.presentation.peercard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.tubetoast.tether.ui.feature.PeerIdentityAccent
import com.tubetoast.tether.ui.theme.TetherTheme

/**
 * Shared row decoration for all PeerCard variants: flat surface background, bottom divider.
 *
 * When [isPaired] is true, a 3dp vertical strip in the peer-identity color appears along the
 * left edge — indicating an established pairing relationship.
 */
@Composable
internal fun PeerCardShell(
    modifier: Modifier = Modifier,
    isPaired: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = TetherTheme.spacing.lg,
        vertical = TetherTheme.spacing.sm,
    ),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(TetherTheme.spacing.sm),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = TetherTheme.colors
    val borderColor = colors.border
    val strokeWidth = with(LocalDensity.current) { 1.dp.toPx() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = strokeWidth,
                )
            },
    ) {
        if (isPaired) {
            PeerIdentityAccent(identityColor = colors.peerIdentity, width = 3.dp)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}
