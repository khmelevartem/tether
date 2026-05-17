package com.tubetoast.tether.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun CancelTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Cancel",
    enabled: Boolean = true,
) {
    val colors = TetherTheme.colors
    val spacing = TetherTheme.spacing
    val color = if (enabled) colors.accent else colors.textMuted
    val a11yLabel = "$label transfer"
    BasicText(
        text = label,
        style = TetherTheme.typography.bodyLarge.copy(color = color),
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.md)
            .semantics {
                contentDescription = a11yLabel
                role = Role.Button
            },
    )
}
