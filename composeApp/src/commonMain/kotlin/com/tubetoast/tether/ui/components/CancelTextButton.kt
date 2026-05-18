package com.tubetoast.tether.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.tubetoast.tether.ui.theme.TetherTheme
import com.tubetoast.tether.ui.theme.tetherMinTouchTarget

@Composable
fun CancelTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Cancel",
    enabled: Boolean = true,
    a11yLabel: String? = null,
) {
    val colors = TetherTheme.colors
    val spacing = TetherTheme.spacing
    val color = if (enabled) colors.accent else colors.textMuted
    val resolvedA11yLabel = a11yLabel ?: "$label transfer"
    BasicText(
        text = label,
        style = TetherTheme.typography.bodyLarge.copy(color = color),
        modifier = modifier
            .tetherMinTouchTarget()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.md)
            .semantics {
                contentDescription = resolvedA11yLabel
                role = Role.Button
            },
    )
}
