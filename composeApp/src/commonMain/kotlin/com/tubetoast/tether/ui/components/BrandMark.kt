package com.tubetoast.tether.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.tubetoast.tether.ui.theme.TetherTheme

sealed class BrandMarkState {
    data object Idle : BrandMarkState()

    data object Searching : BrandMarkState()

    data class Progress(
        val ratio: Float,
    ) : BrandMarkState()

    data object Success : BrandMarkState()

    data class Error(
        val ratio: Float,
    ) : BrandMarkState()
}

@Composable
fun BrandMark(
    state: BrandMarkState,
    modifier: Modifier = Modifier,
) {
    val colors = TetherTheme.colors
    val accent = colors.accent
    val peerIdentity = colors.peerIdentity
    val textPrimary = colors.textPrimary
    val errorColor = colors.error

    when (state) {
        is BrandMarkState.Searching -> {
            val infiniteTransition = rememberInfiniteTransition(label = "searching")
            val rightAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 0.7f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "rightDotAlpha",
            )
            BrandMarkCanvas(
                modifier = modifier,
                accent = accent,
                peerIdentity = peerIdentity,
                textPrimary = textPrimary,
                errorColor = errorColor,
                state = state,
                rightDotAlpha = rightAlpha,
                progressRatio = 0f,
            )
        }

        is BrandMarkState.Progress -> {
            val animatedRatio by animateFloatAsState(
                targetValue = state.ratio.coerceIn(0f, 1f),
                animationSpec = spring(),
                label = "progressRatio",
            )
            BrandMarkCanvas(
                modifier = modifier,
                accent = accent,
                peerIdentity = peerIdentity,
                textPrimary = textPrimary,
                errorColor = errorColor,
                state = state,
                rightDotAlpha = 1f,
                progressRatio = animatedRatio,
            )
        }

        else -> BrandMarkCanvas(
            modifier = modifier,
            accent = accent,
            peerIdentity = peerIdentity,
            textPrimary = textPrimary,
            errorColor = errorColor,
            state = state,
            rightDotAlpha = 1f,
            progressRatio = if (state is BrandMarkState.Error) state.ratio else 1f,
        )
    }
}

@Composable
private fun BrandMarkCanvas(
    accent: Color,
    peerIdentity: Color,
    textPrimary: Color,
    errorColor: Color,
    state: BrandMarkState,
    rightDotAlpha: Float,
    progressRatio: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(80.dp, 20.dp)) {
        val r = size.height / 2f
        val leftCenter = Offset(r, r)
        val rightCenter = Offset(r + 4 * r, r)
        val lineStroke = 1.2f * r

        drawLine(state, leftCenter, rightCenter, textPrimary, accent, progressRatio, lineStroke)
        drawLeftDot(leftCenter, r, accent)
        drawRightDot(state, rightCenter, r, peerIdentity, errorColor, rightDotAlpha)
    }
}

private fun DrawScope.drawLine(
    state: BrandMarkState,
    leftCenter: Offset,
    rightCenter: Offset,
    textPrimary: Color,
    accent: Color,
    progressRatio: Float,
    strokeWidth: Float,
) {
    val lineStart = Offset(leftCenter.x + leftCenter.x, leftCenter.y)
    val lineEnd = Offset(rightCenter.x - rightCenter.y, rightCenter.y)
    val lineLength = lineEnd.x - lineStart.x

    when (state) {
        is BrandMarkState.Idle, is BrandMarkState.Searching -> {
            drawLine(
                color = textPrimary,
                start = lineStart,
                end = lineEnd,
                strokeWidth = strokeWidth,
            )
        }

        is BrandMarkState.Progress, is BrandMarkState.Success -> {
            val filledEnd = Offset(lineStart.x + lineLength * progressRatio, lineStart.y)
            if (progressRatio < 1f) {
                drawLine(
                    color = textPrimary,
                    start = filledEnd,
                    end = lineEnd,
                    strokeWidth = strokeWidth,
                )
            }
            if (progressRatio > 0f) {
                drawLine(
                    color = accent,
                    start = lineStart,
                    end = filledEnd,
                    strokeWidth = strokeWidth,
                )
            }
        }

        is BrandMarkState.Error -> {
            val filledEnd = Offset(lineStart.x + lineLength * progressRatio, lineStart.y)
            if (progressRatio < 1f) {
                drawLine(
                    color = textPrimary,
                    start = filledEnd,
                    end = lineEnd,
                    strokeWidth = strokeWidth,
                )
            }
            if (progressRatio > 0f) {
                drawLine(
                    color = accent,
                    start = lineStart,
                    end = filledEnd,
                    strokeWidth = strokeWidth,
                )
            }
        }
    }
}

private fun DrawScope.drawLeftDot(center: Offset, r: Float, accent: Color) {
    drawCircle(color = accent, radius = r, center = center)
}

private fun DrawScope.drawRightDot(
    state: BrandMarkState,
    center: Offset,
    r: Float,
    peerIdentity: Color,
    errorColor: Color,
    alpha: Float,
) {
    when (state) {
        is BrandMarkState.Searching -> {
            drawCircle(
                color = peerIdentity.copy(alpha = alpha),
                radius = r - 1.2f * r / 2f,
                center = center,
                style = Stroke(width = 1.2f * r),
            )
        }

        is BrandMarkState.Error -> {
            drawCircle(
                color = errorColor,
                radius = r - 1.2f * r / 2f,
                center = center,
                style = Stroke(width = 1.2f * r),
            )
        }

        else -> drawCircle(color = peerIdentity.copy(alpha = alpha), radius = r, center = center)
    }
}
