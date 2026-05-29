package com.tubetoast.tether.ui.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun TitleText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val resolvedColor = if (color == Color.Unspecified) TetherTheme.colors.textPrimary else color
    BasicText(
        text = text,
        style = TetherTheme.typography.titleMedium.copy(color = resolvedColor),
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun BodyText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val resolvedColor = if (color == Color.Unspecified) TetherTheme.colors.textPrimary else color
    BasicText(
        text = text,
        style = TetherTheme.typography.bodyMedium.copy(color = resolvedColor),
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun LabelText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val resolvedColor = if (color == Color.Unspecified) TetherTheme.colors.textMuted else color
    BasicText(
        text = text,
        style = TetherTheme.typography.labelSmall.copy(color = resolvedColor),
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun NumericText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val resolvedColor = if (color == Color.Unspecified) TetherTheme.colors.textPrimary else color
    BasicText(
        text = text,
        style = TetherTheme.typography.numeric.copy(color = resolvedColor),
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun CaptionText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val resolvedColor = if (color == Color.Unspecified) TetherTheme.colors.textMuted else color
    BasicText(
        text = text,
        style = TetherTheme.typography.labelSmall.copy(color = resolvedColor),
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Preview(name = "TetherText — all scales")
@Composable
private fun PreviewTetherTextScales(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        Column {
            TitleText(text = "Title text (titleMedium)")
            BodyText(text = "Body text (bodyMedium)")
            LabelText(text = "Label text (labelSmall)")
            NumericText(text = "42.0 MB/s (numeric)")
            CaptionText(text = "Caption text (labelSmall)")
        }
    }

@Preview(name = "TetherText — color overrides")
@Composable
private fun PreviewTetherTextColorOverrides(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        Column {
            BodyText(text = "Accent body", color = TetherTheme.colors.accent)
            BodyText(text = "Error body", color = TetherTheme.colors.error)
            LabelText(text = "Primary label", color = TetherTheme.colors.textPrimary)
        }
    }
