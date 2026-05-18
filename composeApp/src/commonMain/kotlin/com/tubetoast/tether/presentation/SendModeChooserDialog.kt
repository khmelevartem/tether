package com.tubetoast.tether.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.ui.theme.TetherTheme
import com.tubetoast.tether.ui.theme.tetherMinTouchTarget

@Composable
fun SendModeChooserDialog(
    target: Device,
    onSendFiles: () -> Unit,
    onSendFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
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
                text = "Send to ${target.name}",
                style = typography.titleMedium.copy(color = colors.textPrimary),
            )
            Spacer(modifier = Modifier.height(spacing.lg))
            BasicText(
                text = "Send files",
                style = typography.bodyLarge.copy(color = colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .tetherMinTouchTarget()
                    .clickable(onClick = onSendFiles)
                    .padding(vertical = spacing.sm)
                    .semantics { role = Role.Button },
            )
            BasicText(
                text = "Send folder",
                style = typography.bodyLarge.copy(color = colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .tetherMinTouchTarget()
                    .clickable(onClick = onSendFolder)
                    .padding(vertical = spacing.sm)
                    .semantics { role = Role.Button },
            )
        }
    }
}
