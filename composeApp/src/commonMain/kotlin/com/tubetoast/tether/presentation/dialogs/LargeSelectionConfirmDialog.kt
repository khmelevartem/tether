package com.tubetoast.tether.presentation.dialogs

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tubetoast.tether.transfer.ByteFormatting
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.ui.components.CancelTextButton
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.theme.TetherColors
import com.tubetoast.tether.ui.theme.TetherTheme
import com.tubetoast.tether.ui.theme.tetherMinTouchTarget
import compose.icons.TablerIcons
import compose.icons.tablericons.Check

@Composable
fun LargeSelectionConfirmDialog(
    fileCount: Int,
    totalBytes: Long,
    peer: PeerIdentity,
    dontShowAgain: Boolean,
    onDontShowAgainToggle: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        LargeSelectionConfirmDialogContent(
            fileCount = fileCount,
            totalBytes = totalBytes,
            peer = peer,
            dontShowAgain = dontShowAgain,
            onDontShowAgainToggle = onDontShowAgainToggle,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

@Composable
fun LargeSelectionConfirmDialogContent(
    fileCount: Int,
    totalBytes: Long,
    peer: PeerIdentity,
    dontShowAgain: Boolean,
    onDontShowAgainToggle: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TetherTheme.colors
    val spacing = TetherTheme.spacing
    val typography = TetherTheme.typography
    val shapes = TetherTheme.shapes

    // Compose has no Role.AlertDialog; mergeDescendants ensures the container is a single
    // focusable unit while Dialog() itself handles focus trapping.
    // TODO(#follow-up): revisit if CMP adds native alertdialog role support.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shapes.lg)
            .background(colors.surfaceRaised)
            .border(spacing.borderWidth, colors.border, shapes.lg)
            .padding(horizontal = spacing.lg, vertical = spacing.xl)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        BasicText(
            text = "Large selection",
            style = typography.titleMedium.copy(color = colors.textPrimary),
        )

        BasicText(
            text = "About to send $fileCount files (${ByteFormatting.formatSize(totalBytes)}) to ${peer.id}. Continue?",
            style = typography.bodyMedium.copy(color = colors.textPrimary),
        )

        DontShowAgainRow(
            checked = dontShowAgain,
            onToggle = onDontShowAgainToggle,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CancelTextButton(
                onClick = onDismiss,
                contentDescription = "Cancel — discard selection",
            )
            SendButton(
                onClick = onConfirm,
                sendLabel = "Send $fileCount files to ${peer.id}",
                modifier = Modifier.padding(start = spacing.md),
            )
        }
    }
}

@Composable
private fun DontShowAgainRow(
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TetherTheme.colors
    val spacing = TetherTheme.spacing
    val typography = TetherTheme.typography

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .tetherMinTouchTarget()
            .semantics {
                role = Role.Checkbox
                contentDescription = "Don't show this warning again for large selections"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        CheckboxBox(checked = checked, colors = colors)
        BasicText(
            text = "Don't show again",
            style = typography.bodyMedium.copy(color = colors.textPrimary),
        )
    }
}

@Composable
private fun CheckboxBox(
    checked: Boolean,
    colors: TetherColors,
    modifier: Modifier = Modifier,
) {
    val shapes = TetherTheme.shapes
    val spacing = TetherTheme.spacing
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(shapes.sm)
            .background(if (checked) colors.accent else colors.surface)
            .border(spacing.borderWidth, if (checked) colors.accent else colors.border, shapes.sm),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.graphics.vector
                    .rememberVectorPainter(TablerIcons.Check),
                contentDescription = null,
                colorFilter = androidx.compose.ui.graphics.ColorFilter
                    .tint(colors.surfaceRaised),
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun SendButton(
    onClick: () -> Unit,
    sendLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = TetherTheme.colors
    Box(
        modifier = modifier
            .tetherMinTouchTarget()
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = sendLabel
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "Send",
            style = TetherTheme.typography.bodyMedium.copy(color = colors.accent),
        )
    }
}

@Preview(name = "LargeSelectionConfirmDialog — unchecked")
@Composable
private fun PreviewUnchecked(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        LargeSelectionConfirmDialogContent(
            fileCount = 47,
            totalBytes = 2_684_354_560L,
            peer = PeerIdentity("Alice's Laptop"),
            dontShowAgain = false,
            onDontShowAgainToggle = {},
            onConfirm = {},
            onDismiss = {},
        )
    }

@Preview(name = "LargeSelectionConfirmDialog — checked")
@Composable
private fun PreviewChecked(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        LargeSelectionConfirmDialogContent(
            fileCount = 47,
            totalBytes = 2_684_354_560L,
            peer = PeerIdentity("Alice's Laptop"),
            dontShowAgain = true,
            onDontShowAgainToggle = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
