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
import androidx.compose.ui.unit.dp
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun CancelTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Cancel",
) {
    BasicText(
        text = label,
        style = TetherTheme.typography.bodyLarge.copy(color = TetherTheme.colors.accent),
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics {
                contentDescription = "Cancel transfer"
                role = Role.Button
            },
    )
}
