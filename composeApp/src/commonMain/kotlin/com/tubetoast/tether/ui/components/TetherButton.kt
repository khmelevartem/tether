package com.tubetoast.tether.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.theme.TetherColors
import com.tubetoast.tether.ui.theme.TetherTheme
import com.tubetoast.tether.ui.theme.tetherMinTouchTarget

enum class ButtonVariant { Primary, Secondary, Destructive, Quiet }

@Composable
fun TetherButton(
    label: String,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    enabled: Boolean = true,
) {
    val labelColor = variantColor(variant, enabled, TetherTheme.colors)

    Box(
        modifier = modifier
            .tetherMinTouchTarget()
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        BodyText(text = label, color = labelColor)
    }
}

private fun variantColor(variant: ButtonVariant, enabled: Boolean, colors: TetherColors): Color {
    if (!enabled) return colors.textMuted
    return when (variant) {
        ButtonVariant.Primary -> colors.accent
        ButtonVariant.Secondary -> colors.textPrimary
        ButtonVariant.Destructive -> colors.error
        ButtonVariant.Quiet -> colors.textMuted
    }
}

@Preview(name = "TetherButton — all variants enabled")
@Composable
private fun PreviewButtonsEnabled(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        Column {
            Row {
                TetherButton(
                    label = "Primary",
                    onClick = {},
                    contentDescription = "Primary action",
                    variant = ButtonVariant.Primary,
                )
                TetherButton(
                    label = "Secondary",
                    onClick = {},
                    contentDescription = "Secondary action",
                    variant = ButtonVariant.Secondary,
                )
            }
            Row {
                TetherButton(
                    label = "Destructive",
                    onClick = {},
                    contentDescription = "Destructive action",
                    variant = ButtonVariant.Destructive,
                )
                TetherButton(
                    label = "Quiet",
                    onClick = {},
                    contentDescription = "Quiet action",
                    variant = ButtonVariant.Quiet,
                )
            }
        }
    }

@Preview(name = "TetherButton — all variants disabled")
@Composable
private fun PreviewButtonsDisabled(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        Column {
            Row {
                TetherButton(
                    label = "Primary",
                    onClick = {},
                    contentDescription = "Primary disabled",
                    variant = ButtonVariant.Primary,
                    enabled = false,
                )
                TetherButton(
                    label = "Secondary",
                    onClick = {},
                    contentDescription = "Secondary disabled",
                    variant = ButtonVariant.Secondary,
                    enabled = false,
                )
            }
            Row {
                TetherButton(
                    label = "Destructive",
                    onClick = {},
                    contentDescription = "Destructive disabled",
                    variant = ButtonVariant.Destructive,
                    enabled = false,
                )
                TetherButton(
                    label = "Quiet",
                    onClick = {},
                    contentDescription = "Quiet disabled",
                    variant = ButtonVariant.Quiet,
                    enabled = false,
                )
            }
        }
    }
