package com.tubetoast.tether.presentation.peercard

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.DevicePlatform
import com.tubetoast.tether.transfer.PeerTransferState
import com.tubetoast.tether.ui.designsystem.BodyText
import com.tubetoast.tether.ui.designsystem.ChevronToggleIcon
import com.tubetoast.tether.ui.designsystem.TitleText
import com.tubetoast.tether.ui.feature.AutoSendToggle
import com.tubetoast.tether.ui.feature.toTablerIcon
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.preview.TransferPreviewFixtures
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
internal fun PeerCardIdle(
    state: PeerTransferState.Idle,
    expanded: Boolean,
    device: Device,
    isOnline: Boolean,
    isAutoSendEnabled: Boolean,
    callbacks: PeerCardCallbacks,
    modifier: Modifier = Modifier,
    isPaired: Boolean = false,
    devicePlatform: DevicePlatform? = null,
) {
    val spacing = TetherTheme.spacing
    val colors = TetherTheme.colors
    val peerName = device.name

    val shellModifier = if (isPaired && !isOnline) modifier.alpha(0.45f) else modifier

    PeerCardShell(
        modifier = shellModifier,
        isPaired = isPaired,
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
            devicePlatform?.let { platform ->
                Image(
                    painter = rememberVectorPainter(platform.toTablerIcon()),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.textMuted),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(spacing.sm))
            }
            Column(modifier = Modifier.weight(1f)) {
                TitleText(text = peerName)
                BodyText(
                    text = if (isOnline) "Online" else "Paired — offline",
                    color = if (isOnline) colors.accent else colors.textMuted,
                    modifier = Modifier.padding(top = spacing.xs),
                )
            }
            ChevronToggleIcon(
                expanded = expanded,
                onClick = callbacks.onToggleExpand,
                contentDescription = if (expanded) "Collapse $peerName settings" else "Expand $peerName settings",
            )
        }

        if (expanded) {
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
            expanded = false,
            device = TransferPreviewFixtures.device,
            isOnline = true,
            isAutoSendEnabled = false,
            callbacks = previewCallbacks(),
            devicePlatform = DevicePlatform.Laptop,
        )
    }

@Preview(name = "PeerCardIdle — collapsed offline paired")
@Composable
private fun PreviewIdleCollapsedOffline(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardIdle(
            state = TransferPreviewFixtures.idleCollapsed,
            expanded = false,
            device = TransferPreviewFixtures.device,
            isOnline = false,
            isAutoSendEnabled = false,
            callbacks = previewCallbacks(),
            isPaired = true,
            devicePlatform = DevicePlatform.Laptop,
        )
    }

@Preview(name = "PeerCardIdle — expanded auto-send off")
@Composable
private fun PreviewIdleExpandedAutoSendOff(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardIdle(
            state = TransferPreviewFixtures.idleExpanded,
            expanded = true,
            device = TransferPreviewFixtures.device,
            isOnline = true,
            isAutoSendEnabled = false,
            callbacks = previewCallbacks(),
            devicePlatform = DevicePlatform.Smartphone,
        )
    }

@Preview(name = "PeerCardIdle — expanded auto-send on")
@Composable
private fun PreviewIdleExpandedAutoSendOn(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        PeerCardIdle(
            state = TransferPreviewFixtures.idleExpanded,
            expanded = true,
            device = TransferPreviewFixtures.device,
            isOnline = true,
            isAutoSendEnabled = true,
            callbacks = previewCallbacks(),
            isPaired = true,
            devicePlatform = DevicePlatform.Smartphone,
        )
    }

private fun previewCallbacks() = PeerCardCallbacks(
    onToggleExpand = {},
    onToggleAutoSend = {},
    onCancel = {},
    onDismiss = {},
    onRetryOutbound = {},
    onShowDetails = {},
    onOpenFiles = {},
)
