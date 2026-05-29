package com.tubetoast.tether.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.theme.TetherTheme

data class ConfirmAction(
    val label: String,
    val contentDescription: String,
    val onClick: () -> Unit,
    val variant: ButtonVariant = ButtonVariant.Primary,
)

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    primary: ConfirmAction,
    secondary: ConfirmAction,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    extras: @Composable () -> Unit = {},
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        ConfirmDialogContent(
            title = title,
            body = body,
            primary = primary,
            secondary = secondary,
            modifier = modifier,
            extras = extras,
        )
    }
}

@Composable
fun ConfirmDialogContent(
    title: String,
    body: String,
    primary: ConfirmAction,
    secondary: ConfirmAction,
    modifier: Modifier = Modifier,
    extras: @Composable () -> Unit = {},
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
        TitleText(text = title)
        BodyText(text = body)
        extras()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TetherButton(
                label = secondary.label,
                onClick = secondary.onClick,
                contentDescription = secondary.contentDescription,
                variant = secondary.variant,
            )
            TetherButton(
                label = primary.label,
                onClick = primary.onClick,
                contentDescription = primary.contentDescription,
                variant = primary.variant,
                modifier = Modifier.padding(start = spacing.md),
            )
        }
    }
}

@Preview(name = "ConfirmDialog — simple")
@Composable
private fun PreviewSimple(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        ConfirmDialogContent(
            title = "Discard changes?",
            body = "This action cannot be undone.",
            primary = ConfirmAction(
                label = "Discard",
                contentDescription = "Discard changes",
                onClick = {},
                variant = ButtonVariant.Destructive,
            ),
            secondary = ConfirmAction(
                label = "Cancel",
                contentDescription = "Cancel",
                onClick = {},
                variant = ButtonVariant.Secondary,
            ),
        )
    }

@Preview(name = "ConfirmDialog — with extras")
@Composable
private fun PreviewWithExtras(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        ConfirmDialogContent(
            title = "Large selection",
            body = "About to send 47 files (2.5 GB) to Alice's Laptop. Continue?",
            primary = ConfirmAction(
                label = "Send",
                contentDescription = "Send 47 files",
                onClick = {},
            ),
            secondary = ConfirmAction(
                label = "Cancel",
                contentDescription = "Cancel — discard selection",
                onClick = {},
                variant = ButtonVariant.Secondary,
            ),
            extras = {
                Checkbox(
                    checked = false,
                    onCheckedChange = {},
                    label = "Don't show again",
                )
            },
        )
    }

@Preview(name = "ConfirmDialog — extras checked")
@Composable
private fun PreviewWithExtrasChecked(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        ConfirmDialogContent(
            title = "Large selection",
            body = "About to send 47 files (2.5 GB) to Alice's Laptop. Continue?",
            primary = ConfirmAction(
                label = "Send",
                contentDescription = "Send 47 files",
                onClick = {},
            ),
            secondary = ConfirmAction(
                label = "Cancel",
                contentDescription = "Cancel — discard selection",
                onClick = {},
                variant = ButtonVariant.Secondary,
            ),
            extras = {
                Checkbox(
                    checked = true,
                    onCheckedChange = {},
                    label = "Don't show again",
                )
            },
        )
    }
