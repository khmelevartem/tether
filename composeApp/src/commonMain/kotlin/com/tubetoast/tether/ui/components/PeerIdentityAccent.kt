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

/**
 * A narrow vertical strip rendered as a standalone composable on the leading edge of a row.
 * Callers place it inside a Row at the start; it fills the parent's height automatically.
 * A standalone composable is preferred over a Modifier extension because the strip is always
 * a visible child element, not a decoration layered on an existing element.
 */
@Composable
fun PeerIdentityAccent(
    identityColor: Color,
    modifier: Modifier = Modifier,
    width: Dp = 4.dp,
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
        PeerIdentityAccent(identityColor = Color(0xFFC77E47))
    }
}

@Preview(name = "PeerIdentityAccent — dark")
@Composable
private fun PreviewPeerIdentityAccentDark() {
    PreviewSurface(darkTheme = true) {
        PeerIdentityAccent(identityColor = Color(0xFFD89968))
    }
}
