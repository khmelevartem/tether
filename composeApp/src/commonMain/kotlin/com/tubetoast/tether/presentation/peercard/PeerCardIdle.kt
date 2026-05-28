package com.tubetoast.tether.presentation.peercard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.tubetoast.tether.presentation.transfer.PeerTransferState
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.ui.components.ChevronToggleIcon
import com.tubetoast.tether.ui.components.InfoIconButton
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.preview.TransferPreviewFixtures
import com.tubetoast.tether.ui.theme.TetherTheme
import com.tubetoast.tether.ui.theme.tetherMinTouchTarget
import compose.icons.TablerIcons
import compose.icons.tablericons.ToggleLeft
import compose.icons.tablericons.ToggleRight

@Composable
fun PeerCardIdle(
    state: PeerTransferState.Idle,
    device: Device,
    isOnline: Boolean,
    isAutoSendEnabled: Boolean,
    callbacks: PeerCardCallbacks,
    modifier: Modifier = Modifier,
) {
    val colors = TetherTheme.colors
    val spacing = TetherTheme.spacing
    val typography = TetherTheme.typography
    val shapes = TetherTheme.shapes
    val peerName = device.name

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shapes.md)
            .background(colors.surfaceRaised)
            .border(spacing.borderWidth, colors.border, shapes.md),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = peerName,
                    style = typography.titleMedium.copy(color = colors.textPrimary),
                )
                BasicText(
                    text = if (isOnline) "Online" else "Paired — offline",
                    style = typography.bodyMedium.copy(
                        color = if (isOnline) colors.accent else colors.textMuted,
                    ),
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
            AutoSendBlock(
                peerName = peerName,
                isAutoSendEnabled = isAutoSendEnabled,
                onToggle = callbacks.onToggleAutoSend,
                onInfoTap = callbacks.onShowAutoSendInfo,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg)
                    .padding(bottom = spacing.md),
            )
        }
    }
}

@Composable
private fun AutoSendBlock(
    peerName: String,
    isAutoSendEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onInfoTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TetherTheme.colors
    val spacing = TetherTheme.spacing
    val typography = TetherTheme.typography

    var infoVisible by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            BasicText(
                text = "Auto-send",
                style = typography.bodyMedium.copy(color = colors.textPrimary),
            )
            Box {
                InfoIconButton(
                    onClick = {
                        infoVisible = !infoVisible
                        onInfoTap()
                    },
                    contentDescription = "More information about auto-send",
                    modifier = Modifier.padding(start = spacing.xs),
                )
                if (infoVisible) {
                    Popup(
                        offset = IntOffset(0, 0),
                        onDismissRequest = { infoVisible = false },
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(TetherTheme.shapes.md)
                                .background(colors.surfaceRaised)
                                .padding(spacing.md),
                        ) {
                            BasicText(
                                text = "Auto-send when this is your only online device.",
                                style = typography.bodyMedium.copy(color = colors.textPrimary),
                            )
                        }
                    }
                }
            }
        }
        AutoSendToggle(
            enabled = isAutoSendEnabled,
            peerName = peerName,
            onToggle = onToggle,
        )
    }
}

@Composable
private fun AutoSendToggle(
    enabled: Boolean,
    peerName: String,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TetherTheme.colors
    val state = if (enabled) "On" else "Off"

    Box(
        modifier = modifier
            .tetherMinTouchTarget()
            .clickable { onToggle(!enabled) }
            .semantics {
                role = Role.Switch
                contentDescription = "Auto-send to $peerName when it's the only online device, currently $state"
            },
        contentAlignment = Alignment.Center,
    ) {
        val icon = if (enabled) TablerIcons.ToggleRight else TablerIcons.ToggleLeft
        val tint = if (enabled) colors.accent else colors.textMuted
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.graphics.vector
                .rememberVectorPainter(icon),
            contentDescription = null,
            colorFilter = androidx.compose.ui.graphics.ColorFilter
                .tint(tint),
            modifier = Modifier.size(28.dp),
        )
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
    onShowAutoSendInfo = {},
    onCancel = {},
    onDismiss = {},
    onRetry = {},
    onShowDetails = {},
    onOpenFiles = {},
)
