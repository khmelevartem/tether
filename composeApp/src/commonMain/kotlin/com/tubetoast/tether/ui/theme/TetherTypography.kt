package com.tubetoast.tether.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import tether.composeapp.generated.resources.Inter_Variable
import tether.composeapp.generated.resources.Res

data class TetherTypography(
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val labelSmall: TextStyle,
    val numeric: TextStyle,
)

@Composable
fun tetherTypography(): TetherTypography {
    val interFamily = FontFamily(
        Font(Res.font.Inter_Variable, weight = FontWeight.Normal),
        Font(Res.font.Inter_Variable, weight = FontWeight.SemiBold),
    )
    val lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )
    return TetherTypography(
        titleLarge = TextStyle(
            fontFamily = interFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.02 * 20).sp,
            lineHeightStyle = lineHeightStyle,
        ),
        titleMedium = TextStyle(
            fontFamily = interFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = (-0.02 * 16).sp,
            lineHeightStyle = lineHeightStyle,
        ),
        bodyLarge = TextStyle(
            fontFamily = interFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.sp,
            lineHeightStyle = lineHeightStyle,
        ),
        bodyMedium = TextStyle(
            fontFamily = interFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
            lineHeightStyle = lineHeightStyle,
        ),
        labelSmall = TextStyle(
            fontFamily = interFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.sp,
            lineHeightStyle = lineHeightStyle,
        ),
        numeric = TextStyle(
            fontFamily = interFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
            lineHeightStyle = lineHeightStyle,
            fontFeatureSettings = "tnum",
        ),
    )
}
