package com.tubetoast.tether.presentation.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tubetoast.tether.transfer.ByteFormatting
import com.tubetoast.tether.ui.designsystem.ButtonVariant
import com.tubetoast.tether.ui.designsystem.Checkbox
import com.tubetoast.tether.ui.designsystem.ConfirmAction
import com.tubetoast.tether.ui.designsystem.ConfirmDialogContent
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes

@Composable
fun LargeSelectionConfirmDialog(
    fileCount: Int,
    totalBytes: Long,
    peerName: String,
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
            peerName = peerName,
            dontShowAgain = dontShowAgain,
            onDontShowAgainToggle = onDontShowAgainToggle,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun LargeSelectionConfirmDialogContent(
    fileCount: Int,
    totalBytes: Long,
    peerName: String,
    dontShowAgain: Boolean,
    onDontShowAgainToggle: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConfirmDialogContent(
        title = "Large selection",
        body = "About to send $fileCount files (${ByteFormatting.formatSize(totalBytes)}) to $peerName. Continue?",
        primary = ConfirmAction(
            label = "Send",
            contentDescription = "Send $fileCount files to $peerName",
            onClick = onConfirm,
        ),
        secondary = ConfirmAction(
            label = "Cancel",
            contentDescription = "Cancel — discard selection",
            onClick = onDismiss,
            variant = ButtonVariant.Secondary,
        ),
        modifier = modifier,
        extras = {
            Checkbox(
                checked = dontShowAgain,
                onCheckedChange = onDontShowAgainToggle,
                label = "Don't show again",
            )
        },
    )
}

@Preview(name = "LargeSelectionConfirmDialog — unchecked")
@Composable
private fun PreviewUnchecked(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        LargeSelectionConfirmDialogContent(
            fileCount = 47,
            totalBytes = 2_684_354_560L,
            peerName = "Alice's Laptop",
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
            peerName = "Alice's Laptop",
            dontShowAgain = true,
            onDontShowAgainToggle = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
