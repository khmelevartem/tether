package com.tubetoast.tether.ui.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.preview.Themes
import com.tubetoast.tether.ui.theme.TetherTheme

/**
 * @param indeterminate when true, [progress] is ignored and a segment sweeps across the track —
 *   for phases whose duration is unknown (e.g. a photo exporting before its transfer starts).
 */
@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
    height: Dp = 4.dp,
    indeterminate: Boolean = false,
) {
    val colors = TetherTheme.colors
    val resolvedColor = if (color == Color.Unspecified) colors.accent else color
    val resolvedTrackColor = if (trackColor == Color.Unspecified) colors.border else trackColor
    val shapes = TetherTheme.shapes

    if (indeterminate) {
        IndeterminateProgressBar(resolvedColor, resolvedTrackColor, height, shapes.sm, modifier)
        return
    }

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

@Composable
private fun IndeterminateProgressBar(
    color: Color,
    trackColor: Color,
    height: Dp,
    cornerShape: Shape,
    modifier: Modifier = Modifier,
) {
    val segment = 0.3f
    val head by rememberInfiniteTransition(label = "indeterminateProgress").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "indeterminateHead",
    )
    BoxWithConstraints(
        modifier = modifier
            .height(height)
            .clip(cornerShape)
            .background(trackColor),
    ) {
        // Sweep the segment from fully off the left edge to fully off the right edge.
        val offsetX = maxWidth * (head * (1f + segment) - segment)
        Box(
            modifier = Modifier
                .offset(x = offsetX)
                .fillMaxWidth(segment)
                .height(height)
                .clip(cornerShape)
                .background(color),
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
