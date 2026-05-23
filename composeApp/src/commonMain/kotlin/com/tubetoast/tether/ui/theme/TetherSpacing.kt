package com.tubetoast.tether.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class TetherSpacing(
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
    val xxl: Dp,
    /** Width of the 1dp surface-tier separator specified in the style guide. */
    val borderWidth: Dp,
)

val DefaultSpacing = TetherSpacing(
    xs = 4.dp,
    sm = 8.dp,
    md = 12.dp,
    lg = 16.dp,
    xl = 24.dp,
    xxl = 32.dp,
    borderWidth = 1.dp,
)

val LocalTetherSpacing = staticCompositionLocalOf { DefaultSpacing }
