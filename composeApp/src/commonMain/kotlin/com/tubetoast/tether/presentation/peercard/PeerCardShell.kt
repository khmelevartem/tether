package com.tubetoast.tether.presentation.peercard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

private val PeerIdentityStripWidth = 3.dp

/**
 * When [isPaired] is true, a peer-identity accent marks the card, indicating an established
 * pairing relationship.
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
    val spacing = TetherTheme.spacing
    val borderColor = colors.border
    val borderWidthPx = with(LocalDensity.current) { spacing.borderWidth.toPx() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(colors.surface)
            .drawBehind {
                val y = size.height - borderWidthPx / 2f
                drawLine(
                    color = borderColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = borderWidthPx,
                )
            },
    ) {
        if (isPaired) {
            PeerIdentityAccent(identityColor = colors.peerIdentity, width = PeerIdentityStripWidth)
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
