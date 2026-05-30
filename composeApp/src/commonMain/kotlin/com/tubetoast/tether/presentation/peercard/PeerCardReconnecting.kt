package com.tubetoast.tether.presentation.peercard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.tubetoast.tether.presentation.transfer.PeerTransferState
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.ui.designsystem.BodyText
import com.tubetoast.tether.ui.designsystem.TitleText
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.preview.TransferPreviewFixtures
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
internal fun PeerCardReconnecting(
    state: PeerTransferState.Reconnecting,
    device: Device,
    modifier: Modifier = Modifier,
) {
    val spacing = TetherTheme.spacing
    val peerName = device.name
    val announcement = "Connection lost. Reconnecting to $peerName…"

    PeerCardShell(
        modifier = modifier.semantics {
            liveRegion = LiveRegionMode.Assertive
            contentDescription = announcement
        },
        verticalArrangement = Arrangement.spacedBy(spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TitleText(text = peerName, modifier = Modifier.fillMaxWidth())
        BodyText(text = reconnectingCardCopy(state), color = TetherTheme.colors.textMuted)
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
