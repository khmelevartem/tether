package com.tubetoast.tether.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

@Composable
fun TetherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) darkTetherColors() else lightTetherColors()
    val typography = tetherTypography()
    val spacing = TetherSpacing()
    val shapes = TetherShapes()
    CompositionLocalProvider(
        LocalTetherColors provides colors,
        LocalTetherTypography provides typography,
        LocalTetherSpacing provides spacing,
        LocalTetherShapes provides shapes,
        content = content,
    )
}

object TetherTheme {
    val colors: TetherColors
        @Composable @ReadOnlyComposable
        get() = LocalTetherColors.current

    val typography: TetherTypography
        @Composable @ReadOnlyComposable
        get() = LocalTetherTypography.current

    val spacing: TetherSpacing
        @Composable @ReadOnlyComposable
        get() = LocalTetherSpacing.current

    val shapes: TetherShapes
        @Composable @ReadOnlyComposable
        get() = LocalTetherShapes.current
}
