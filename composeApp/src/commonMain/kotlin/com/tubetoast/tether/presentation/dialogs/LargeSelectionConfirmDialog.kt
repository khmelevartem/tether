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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tubetoast.tether.transfer.ByteFormatting
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.ui.components.BodyText
import com.tubetoast.tether.ui.components.CancelTextButton
import com.tubetoast.tether.ui.components.Checkbox
import com.tubetoast.tether.ui.components.TitleText
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.theme.TetherTheme
import com.tubetoast.tether.ui.theme.tetherMinTouchTarget

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
        TitleText(text = "Large selection")

        BodyText(
            text = "About to send $fileCount files (${ByteFormatting.formatSize(totalBytes)}) to ${peer.id}. Continue?",
        )

        Checkbox(
            checked = dontShowAgain,
            onCheckedChange = onDontShowAgainToggle,
            label = "Don't show again",
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
        BodyText(
            text = "Send",
            color = colors.accent,
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
