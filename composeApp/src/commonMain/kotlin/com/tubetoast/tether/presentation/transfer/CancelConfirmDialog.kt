package com.tubetoast.tether.presentation.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun CancelConfirmDialog(
    onStopTransfer: () -> Unit,
    onKeepSending: () -> Unit,
) {
    val keepSendingFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onKeepSending) {
        val colors = TetherTheme.colors
        val typography = TetherTheme.typography
        val shapes = TetherTheme.shapes
        val spacing = TetherTheme.spacing
        Column(
            modifier = Modifier
                .clip(shapes.md)
                .background(colors.surfaceRaised)
                .padding(spacing.xl),
        ) {
            BasicText(
                text = "Cancel transfer?",
                style = typography.titleMedium.copy(color = colors.textPrimary),
            )
            Spacer(modifier = Modifier.height(spacing.sm))
            BasicText(
                text = "The transfer will stop and any files not yet sent will not arrive.",
                style = typography.bodyMedium.copy(color = colors.textMuted),
            )
            Spacer(modifier = Modifier.height(spacing.xl))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                BasicText(
                    text = "Stop transfer",
                    style = typography.bodyLarge.copy(color = colors.error),
                    modifier = Modifier
                        .clickable(onClick = onStopTransfer)
                        .padding(spacing.sm)
                        .semantics { role = Role.Button },
                )
                BasicText(
                    text = "Keep sending",
                    style = typography.bodyLarge.copy(color = colors.accent),
                    modifier = Modifier
                        .focusRequester(keepSendingFocus)
                        .focusable()
                        .clickable(onClick = onKeepSending)
                        .padding(spacing.sm)
                        .semantics { role = Role.Button },
                )
            }
        }
    }
    LaunchedEffect(Unit) {
        keepSendingFocus.requestFocus()
    }
}
