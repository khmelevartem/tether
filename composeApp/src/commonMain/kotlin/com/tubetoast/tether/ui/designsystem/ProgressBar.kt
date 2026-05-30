package com.tubetoast.tether.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
    height: Dp = 4.dp,
) {
    val colors = TetherTheme.colors
    val resolvedColor = if (color == Color.Unspecified) colors.accent else color
    val resolvedTrackColor = if (trackColor == Color.Unspecified) colors.border else trackColor
    val shapes = TetherTheme.shapes

    Box(
        modifier = modifier
            .height(height)
            .clip(shapes.sm)
            .background(resolvedTrackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .clip(shapes.sm)
                .background(resolvedColor),
        )
    }
}

@Preview(name = "ProgressBar — 0%")
@Composable
private fun PreviewProgressBar0(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        ProgressBar(progress = 0f, modifier = Modifier.fillMaxWidth())
    }

@Preview(name = "ProgressBar — 50%")
@Composable
private fun PreviewProgressBar50(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        ProgressBar(progress = 0.5f, modifier = Modifier.fillMaxWidth())
    }

@Preview(name = "ProgressBar — 100%")
@Composable
private fun PreviewProgressBar100(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        ProgressBar(progress = 1f, modifier = Modifier.fillMaxWidth())
    }

@Preview(name = "ProgressBar — track only (0% fill)")
@Composable
private fun PreviewProgressBarTrackOnly(@PreviewParameter(Themes::class) dark: Boolean) =
    PreviewSurface(darkTheme = dark) {
        ProgressBar(progress = 0f, modifier = Modifier.fillMaxWidth(), height = 4.dp)
    }
