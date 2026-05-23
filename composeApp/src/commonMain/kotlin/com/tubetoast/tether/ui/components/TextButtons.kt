package com.tubetoast.tether.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.theme.TetherTheme
import com.tubetoast.tether.ui.theme.tetherMinTouchTarget

@Composable
fun CancelTextButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = TetherTheme.colors
    Box(
        modifier = modifier
            .tetherMinTouchTarget()
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "Cancel",
            style = TetherTheme.typography.bodyMedium.copy(
                color = if (enabled) colors.accent else colors.textMuted,
            ),
        )
    }
}

@Composable
fun RetryTextButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = TetherTheme.colors
    Box(
        modifier = modifier
            .tetherMinTouchTarget()
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "Retry",
            style = TetherTheme.typography.bodyMedium.copy(
                color = if (enabled) colors.accent else colors.textMuted,
            ),
        )
    }
}

@Composable
fun ShowDetailsButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = TetherTheme.colors
    Box(
        modifier = modifier
            .tetherMinTouchTarget()
            .clickable(onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "Show details →",
            style = TetherTheme.typography.bodyMedium.copy(color = colors.accent),
        )
    }
}

@Preview(name = "Text buttons — light")
@Composable
private fun PreviewTextButtonsLight() {
    PreviewSurface {
        androidx.compose.foundation.layout.Row {
            CancelTextButton(onClick = {}, contentDescription = "Cancel transfer")
            CancelTextButton(onClick = {}, contentDescription = "Cancel transfer", enabled = false)
            RetryTextButton(onClick = {}, contentDescription = "Retry sending")
            RetryTextButton(onClick = {}, contentDescription = "Retry sending", enabled = false)
            ShowDetailsButton(onClick = {}, contentDescription = "Show transfer details")
        }
    }
}

@Preview(name = "Text buttons — dark")
@Composable
private fun PreviewTextButtonsDark() {
    PreviewSurface(darkTheme = true) {
        androidx.compose.foundation.layout.Row {
            CancelTextButton(onClick = {}, contentDescription = "Cancel transfer")
            RetryTextButton(onClick = {}, contentDescription = "Retry sending")
            ShowDetailsButton(onClick = {}, contentDescription = "Show transfer details")
        }
    }
}
