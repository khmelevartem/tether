package com.tubetoast.tether.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.theme.TetherTheme

/** Matches `TetherSpacing.xs` (4dp) for visual consistency with the spacing scale. */
private val DefaultAccentWidth = 4.dp

@Composable
fun PeerIdentityAccent(
    identityColor: Color,
    modifier: Modifier = Modifier,
    width: Dp = DefaultAccentWidth,
) {
    Box(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(identityColor),
    )
}

@Preview(name = "PeerIdentityAccent — light")
@Composable
private fun PreviewPeerIdentityAccentLight() {
    PreviewSurface {
        PeerIdentityAccent(identityColor = TetherTheme.colors.peerIdentity)
    }
}

@Preview(name = "PeerIdentityAccent — dark")
@Composable
private fun PreviewPeerIdentityAccentDark() {
    PreviewSurface(darkTheme = true) {
        PeerIdentityAccent(identityColor = TetherTheme.colors.peerIdentity)
    }
}
