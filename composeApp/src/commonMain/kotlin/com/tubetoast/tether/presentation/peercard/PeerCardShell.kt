package com.tubetoast.tether.presentation.peercard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tubetoast.tether.ui.designsystem.tetherRowDecoration
import com.tubetoast.tether.ui.theme.TetherTheme

/**
 * When [isPaired] is true, a peer-identity accent strip is painted along the card's left edge,
 * indicating an established pairing relationship. The strip is drawn at the final measured size,
 * so it stays correct under any parent constraints (including the unbounded height a LazyColumn
 * hands its items).
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .tetherRowDecoration(leadingAccent = if (isPaired) colors.peerIdentity else null)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}
