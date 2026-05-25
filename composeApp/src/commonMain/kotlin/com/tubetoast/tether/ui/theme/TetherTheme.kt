package com.tubetoast.tether.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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

@Composable
fun TetherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val typography = rememberTetherTypography()
    CompositionLocalProvider(
        LocalTetherColors provides colors,
        LocalTetherTypography provides typography,
        LocalTetherSpacing provides DefaultSpacing,
        LocalTetherShapes provides DefaultShapes,
        content = content,
    )
}

fun Modifier.tetherMinTouchTarget(): Modifier = this.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
