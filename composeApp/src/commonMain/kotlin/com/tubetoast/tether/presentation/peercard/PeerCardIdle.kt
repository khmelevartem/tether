package com.tubetoast.tether.presentation.peercard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.tubetoast.tether.presentation.transfer.PeerTransferState
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.ui.designsystem.BodyText
import com.tubetoast.tether.ui.designsystem.ChevronToggleIcon
import com.tubetoast.tether.ui.designsystem.TitleText
import com.tubetoast.tether.ui.feature.AutoSendToggle
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.preview.TransferPreviewFixtures
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun PeerCardIdle(
    state: PeerTransferState.Idle,
    device: Device,
    isOnline: Boolean,
    isAutoSendEnabled: Boolean,
    callbacks: PeerCardCallbacks,
    modifier: Modifier = Modifier,
) {
    val spacing = TetherTheme.spacing
    val colors = TetherTheme.colors
    val peerName = device.name

    PeerCardShell(
        modifier = modifier,
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                TitleText(text = peerName)
                BodyText(
                    text = if (isOnline) "Online" else "Paired — offline",
                    color = if (isOnline) colors.accent else colors.textMuted,
                    modifier = Modifier.padding(top = spacing.xs),
                )
            }
            ChevronToggleIcon(
                expanded = state.expanded,
                onClick = callbacks.onToggleExpand,
                contentDescription = if (state.expanded) "Collapse $peerName settings" else "Expand $peerName settings",
            )
        }

        if (state.expanded) {
            AutoSendToggle(
                enabled = isAutoSendEnabled,
                onToggle = callbacks.onToggleAutoSend,
                accessibilityHint = "when $peerName is the only online device",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg)
                    .padding(bottom = spacing.md),
            )
        }
    }
}

@Preview(name = "PeerCardIdle — collapsed online")
@Composable
private fun PreviewIdleCollapsedOnline(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardIdle(
            state = TransferPreviewFixtures.idleCollapsed,
            device = TransferPreviewFixtures.device,
            isOnline = true,
            isAutoSendEnabled = false,
            callbacks = previewCallbacks(),
        )
    }

@Preview(name = "PeerCardIdle — collapsed offline")
@Composable
private fun PreviewIdleCollapsedOffline(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardIdle(
            state = TransferPreviewFixtures.idleCollapsed,
            device = TransferPreviewFixtures.device,
            isOnline = false,
            isAutoSendEnabled = false,
            callbacks = previewCallbacks(),
        )
    }

@Preview(name = "PeerCardIdle — expanded auto-send off")
@Composable
private fun PreviewIdleExpandedAutoSendOff(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardIdle(
            state = TransferPreviewFixtures.idleExpanded,
            device = TransferPreviewFixtures.device,
            isOnline = true,
            isAutoSendEnabled = false,
            callbacks = previewCallbacks(),
        )
    }

@Preview(name = "PeerCardIdle — expanded auto-send on")
@Composable
private fun PreviewIdleExpandedAutoSendOn(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardIdle(
            state = TransferPreviewFixtures.idleExpanded,
            device = TransferPreviewFixtures.device,
            isOnline = true,
            isAutoSendEnabled = true,
            callbacks = previewCallbacks(),
        )
    }

private fun previewCallbacks() = PeerCardCallbacks(
    onToggleExpand = {},
    onToggleAutoSend = {},
    onCancel = {},
    onDismiss = {},
    onRetry = {},
    onShowDetails = {},
    onOpenFiles = {},
)
