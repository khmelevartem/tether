package com.tubetoast.tether.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.tubetoast.tether.ui.theme.TetherTheme

internal val RowAccentBarWidth = 3.dp

@Composable
internal fun Modifier.tetherRowDecoration(
    leadingAccent: Color? = null,
    bottomDivider: Boolean = true,
): Modifier {
    val colors = TetherTheme.colors
    val spacing = TetherTheme.spacing
    val density = LocalDensity.current
    val borderColor = colors.border
    val borderWidthPx = with(density) { spacing.borderWidth.toPx() }
    val accentBarWidthPx = with(density) { RowAccentBarWidth.toPx() }

    return drawBehind {
        if (bottomDivider) {
            drawLine(
                color = borderColor,
                start = Offset(0f, size.height - borderWidthPx / 2f),
                end = Offset(size.width, size.height - borderWidthPx / 2f),
                strokeWidth = borderWidthPx,
            )
        }
        if (leadingAccent != null) {
            drawRect(
                color = leadingAccent,
                topLeft = Offset.Zero,
                size = Size(accentBarWidthPx, size.height),
            )
        }
    }
}
