package com.tubetoast.tether.presentation.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun FolderSendConfirmDialog(
    fileCount: Int,
    totalBytes: Long,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel) {
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
                text = "About to send $fileCount files (${formatBytes(totalBytes)}). Continue?",
                style = typography.bodyMedium.copy(color = colors.textMuted),
            )
            Spacer(modifier = Modifier.height(spacing.xl))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                BasicText(
                    text = "Cancel",
                    style = typography.bodyLarge.copy(color = colors.textMuted),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(onClick = onCancel)
                        .padding(spacing.sm)
                        .semantics { role = Role.Button },
                )
                BasicText(
                    text = "Continue",
                    style = typography.bodyLarge.copy(color = colors.accent),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(onClick = onContinue)
                        .padding(spacing.sm)
                        .semantics { role = Role.Button },
                )
            }
        }
    }
}
