package com.tubetoast.tether.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

@Immutable
data class TetherShapes(
    val sm: RoundedCornerShape,
    val md: RoundedCornerShape,
    val lg: RoundedCornerShape,
)

val DefaultShapes = TetherShapes(
    sm = RoundedCornerShape(6.dp),
    md = RoundedCornerShape(10.dp),
    lg = RoundedCornerShape(14.dp),
)

val LocalTetherShapes = staticCompositionLocalOf { DefaultShapes }
