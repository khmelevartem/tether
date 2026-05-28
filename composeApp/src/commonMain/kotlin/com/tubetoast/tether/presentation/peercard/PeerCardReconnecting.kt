package com.tubetoast.tether.presentation.peercard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.tubetoast.tether.presentation.transfer.PeerTransferState
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.preview.TransferPreviewFixtures
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun PeerCardReconnecting(
    state: PeerTransferState.Reconnecting,
    device: Device,
    modifier: Modifier = Modifier,
) {
    val colors = TetherTheme.colors
    val spacing = TetherTheme.spacing
    val typography = TetherTheme.typography
    val shapes = TetherTheme.shapes
    val peerName = device.name
    val announcement = "Connection lost. Reconnecting to $peerName…"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shapes.md)
            .background(colors.surfaceRaised)
            .border(spacing.borderWidth, colors.border, shapes.md)
            .padding(horizontal = spacing.lg, vertical = spacing.md)
            .semantics {
                liveRegion = LiveRegionMode.Assertive
                contentDescription = announcement
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        BasicText(
            text = peerName,
            style = typography.titleMedium.copy(color = colors.textPrimary),
            modifier = Modifier.fillMaxWidth(),
        )
        BasicText(
            text = reconnectingCardCopy(state),
            style = typography.bodyMedium.copy(color = colors.textMuted),
        )
    }
}

@Preview(name = "PeerCardReconnecting")
@Composable
private fun PreviewReconnecting(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardReconnecting(
            state = TransferPreviewFixtures.reconnecting,
            device = TransferPreviewFixtures.device,
        )
    }
