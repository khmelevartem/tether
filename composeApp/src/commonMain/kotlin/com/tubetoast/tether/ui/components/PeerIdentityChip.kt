package com.tubetoast.tether.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun PeerIdentityChip(
    name: String,
    modifier: Modifier = Modifier,
) {
    val colors = TetherTheme.colors
    val shapes = TetherTheme.shapes
    BasicText(
        text = name,
        style = TetherTheme.typography.labelSmall.copy(color = colors.textPrimary),
        modifier = modifier
            .clip(shapes.sm)
            .background(colors.peerIdentity.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
