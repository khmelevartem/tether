package com.tubetoast.tether.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.theme.TetherTheme
import kotlinx.coroutines.delay

sealed interface BrandMarkState {
    data object Idle : BrandMarkState

    data object Searching : BrandMarkState

    data class TransferProgress(
        val progress: Float,
    ) : BrandMarkState

    data object Success : BrandMarkState

    data class Error(
        val progress: Float,
    ) : BrandMarkState

    data object Disconnected : BrandMarkState
}

object BrandMark {
    val DefaultWidth = 96.dp
    val DefaultHeight = 24.dp
}

private fun BrandMarkState.defaultContentDescription(): String? = when (this) {
    is BrandMarkState.TransferProgress -> "Transfer in progress"
    is BrandMarkState.Success -> "Transfer complete"
    is BrandMarkState.Error -> "Transfer failed"
    is BrandMarkState.Searching -> "Searching for peer"
    else -> null
}

@Composable
fun BrandMark(
    state: BrandMarkState,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val effectiveContentDescription = contentDescription ?: state.defaultContentDescription()
    val semanticsModifier = if (effectiveContentDescription != null) {
        modifier.semantics { this.contentDescription = effectiveContentDescription }
    } else {
        modifier
    }

    val accentColor = TetherTheme.colors.accent
    val peerColor = TetherTheme.colors.peerIdentity
    val lineColor = TetherTheme.colors.textPrimary
    val errorColor = TetherTheme.colors.error

    when (state) {
        is BrandMarkState.Searching -> {
            val infiniteTransition = rememberInfiniteTransition(label = "searching")
            val rightDotAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 0.7f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "rightDotAlpha",
            )
            BrandMarkCanvas(
                modifier = semanticsModifier,
                accentColor = accentColor,
                peerColor = peerColor,
                lineColor = lineColor,
                errorColor = errorColor,
                state = state,
                rightDotAlpha = rightDotAlpha,
                rightDotScale = 1f,
                rightDotColorOverride = null,
            )
        }

        is BrandMarkState.Success -> {
            var rightDotColorFraction by remember { mutableFloatStateOf(0f) }
            var rightDotScale by remember { mutableFloatStateOf(1f) }
            val animatedColorFraction by animateFloatAsState(
                targetValue = rightDotColorFraction,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                label = "successColorFraction",
            )
            val animatedScale by animateFloatAsState(
                targetValue = rightDotScale,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                label = "successScale",
            )
            LaunchedEffect(Unit) {
                rightDotColorFraction = 1f
                rightDotScale = 1.05f
                delay(500)
                rightDotColorFraction = 0f
                rightDotScale = 1f
            }
            val rightDotColorOverride = lerp(peerColor, accentColor, animatedColorFraction)
            BrandMarkCanvas(
                modifier = semanticsModifier,
                accentColor = accentColor,
                peerColor = peerColor,
                lineColor = lineColor,
                errorColor = errorColor,
                state = state,
                rightDotAlpha = 1f,
                rightDotScale = animatedScale,
                rightDotColorOverride = rightDotColorOverride,
            )
        }

        else -> {
            BrandMarkCanvas(
                modifier = semanticsModifier,
                accentColor = accentColor,
                peerColor = peerColor,
                lineColor = lineColor,
                errorColor = errorColor,
                state = state,
                rightDotAlpha = 1f,
                rightDotScale = 1f,
                rightDotColorOverride = null,
            )
        }
    }
}

@Composable
private fun BrandMarkCanvas(
    accentColor: Color,
    peerColor: Color,
    lineColor: Color,
    errorColor: Color,
    state: BrandMarkState,
    rightDotAlpha: Float,
    rightDotScale: Float,
    rightDotColorOverride: Color?,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(BrandMark.DefaultWidth, BrandMark.DefaultHeight)) {
        val r = size.height / 2f
        val leftCenter = Offset(r, r)
        val rightCenter = Offset(r + 4 * r, r)
        val strokeWeight = 1.2f * r

        drawConnectingLine(state, leftCenter, rightCenter, strokeWeight, accentColor, lineColor)
        drawCircle(color = accentColor, radius = r, center = leftCenter)
        drawRightDot(
            state,
            errorColor,
            peerColor,
            rightDotColorOverride,
            rightDotAlpha,
            rightDotScale,
            r,
            rightCenter,
            strokeWeight,
        )
    }
}

private fun DrawScope.drawConnectingLine(
    state: BrandMarkState,
    leftCenter: Offset,
    rightCenter: Offset,
    strokeWeight: Float,
    accentColor: Color,
    lineColor: Color,
) {
    when (state) {
        is BrandMarkState.Disconnected -> drawDisconnectedLine(leftCenter, rightCenter, strokeWeight, lineColor)
        is BrandMarkState.TransferProgress -> drawProgressLine(
            state.progress,
            leftCenter,
            rightCenter,
            strokeWeight,
            accentColor,
            lineColor,
        )
        is BrandMarkState.Error -> drawErrorLine(state.progress, leftCenter, rightCenter, strokeWeight, accentColor)
        is BrandMarkState.Success -> drawLine(
            color = accentColor,
            start = Offset(leftCenter.x, leftCenter.y),
            end = Offset(rightCenter.x, rightCenter.y),
            strokeWidth = strokeWeight,
            cap = StrokeCap.Butt,
        )
        else -> drawLine(
            color = lineColor,
            start = Offset(leftCenter.x, leftCenter.y),
            end = Offset(rightCenter.x, rightCenter.y),
            strokeWidth = strokeWeight,
            cap = StrokeCap.Butt,
        )
    }
}

private fun DrawScope.drawDisconnectedLine(
    leftCenter: Offset,
    rightCenter: Offset,
    strokeWeight: Float,
    lineColor: Color,
) {
    val r = leftCenter.x
    val segmentLength = r
    val gapLength = 2 * r
    val lineY = leftCenter.y
    val startX = leftCenter.x
    drawLine(
        color = lineColor,
        start = Offset(startX, lineY),
        end = Offset(startX + segmentLength, lineY),
        strokeWidth = strokeWeight,
        cap = StrokeCap.Butt,
    )
    drawLine(
        color = lineColor,
        start = Offset(startX + segmentLength + gapLength, lineY),
        end = Offset(rightCenter.x, lineY),
        strokeWidth = strokeWeight,
        cap = StrokeCap.Butt,
    )
}

private fun DrawScope.drawProgressLine(
    progress: Float,
    leftCenter: Offset,
    rightCenter: Offset,
    strokeWeight: Float,
    accentColor: Color,
    lineColor: Color,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val lineStart = leftCenter.x
    val lineEnd = rightCenter.x
    val lineLength = lineEnd - lineStart
    val filledEnd = lineStart + lineLength * clamped
    if (clamped > 0f) {
        drawLine(
            color = accentColor,
            start = Offset(lineStart, leftCenter.y),
            end = Offset(filledEnd, leftCenter.y),
            strokeWidth = strokeWeight,
            cap = StrokeCap.Butt,
        )
    }
    if (clamped < 1f) {
        drawLine(
            color = lineColor,
            start = Offset(filledEnd, leftCenter.y),
            end = Offset(lineEnd, leftCenter.y),
            strokeWidth = strokeWeight,
            cap = StrokeCap.Butt,
        )
    }
}

private fun DrawScope.drawErrorLine(
    progress: Float,
    leftCenter: Offset,
    rightCenter: Offset,
    strokeWeight: Float,
    accentColor: Color,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val lineStart = leftCenter.x
    val lineEnd = rightCenter.x
    val lineLength = lineEnd - lineStart
    val filledEnd = lineStart + lineLength * clamped
    if (clamped > 0f) {
        drawLine(
            color = accentColor,
            start = Offset(lineStart, leftCenter.y),
            end = Offset(filledEnd, leftCenter.y),
            strokeWidth = strokeWeight,
            cap = StrokeCap.Butt,
        )
    }
}

private fun DrawScope.drawRightDot(
    state: BrandMarkState,
    errorColor: Color,
    peerColor: Color,
    rightDotColorOverride: Color?,
    rightDotAlpha: Float,
    rightDotScale: Float,
    r: Float,
    rightCenter: Offset,
    strokeWeight: Float,
) {
    val effectivePeerColor = rightDotColorOverride ?: peerColor
    val scaledR = r * rightDotScale

    when (state) {
        is BrandMarkState.Searching -> drawCircle(
            color = effectivePeerColor.copy(alpha = rightDotAlpha),
            radius = scaledR - strokeWeight / 2f,
            center = rightCenter,
            style = Stroke(strokeWeight),
        )
        is BrandMarkState.Error -> drawCircle(
            color = errorColor,
            radius = scaledR - strokeWeight / 2f,
            center = rightCenter,
            style = Stroke(strokeWeight),
        )
        else -> drawCircle(
            color = effectivePeerColor.copy(alpha = rightDotAlpha),
            radius = scaledR,
            center = rightCenter,
        )
    }
}

@Preview(name = "BrandMark — Idle (Light)")
@Composable
private fun PreviewBrandMarkIdle() {
    PreviewSurface { BrandMark(BrandMarkState.Idle) }
}

@Preview(name = "BrandMark — Searching (Light)")
@Composable
private fun PreviewBrandMarkSearching() {
    PreviewSurface { BrandMark(BrandMarkState.Searching) }
}

@Preview(name = "BrandMark — Progress 60% (Light)")
@Composable
private fun PreviewBrandMarkProgress() {
    PreviewSurface { BrandMark(BrandMarkState.TransferProgress(progress = 0.6f)) }
}

@Preview(name = "BrandMark — Success (Light)")
@Composable
private fun PreviewBrandMarkSuccess() {
    PreviewSurface { BrandMark(BrandMarkState.Success) }
}

@Preview(name = "BrandMark — Error 40% (Light)")
@Composable
private fun PreviewBrandMarkError() {
    PreviewSurface { BrandMark(BrandMarkState.Error(progress = 0.4f)) }
}

@Preview(name = "BrandMark — Disconnected (Light)")
@Composable
private fun PreviewBrandMarkDisconnected() {
    PreviewSurface { BrandMark(BrandMarkState.Disconnected) }
}

@Preview(name = "BrandMark — Idle (Dark)")
@Composable
private fun PreviewBrandMarkIdleDark() {
    PreviewSurface(darkTheme = true) { BrandMark(BrandMarkState.Idle) }
}

@Preview(name = "BrandMark — Searching (Dark)")
@Composable
private fun PreviewBrandMarkSearchingDark() {
    PreviewSurface(darkTheme = true) { BrandMark(BrandMarkState.Searching) }
}

@Preview(name = "BrandMark — Progress 60% (Dark)")
@Composable
private fun PreviewBrandMarkProgressDark() {
    PreviewSurface(darkTheme = true) { BrandMark(BrandMarkState.TransferProgress(progress = 0.6f)) }
}

@Preview(name = "BrandMark — Success (Dark)")
@Composable
private fun PreviewBrandMarkSuccessDark() {
    PreviewSurface(darkTheme = true) { BrandMark(BrandMarkState.Success) }
}

@Preview(name = "BrandMark — Error 40% (Dark)")
@Composable
private fun PreviewBrandMarkErrorDark() {
    PreviewSurface(darkTheme = true) {
        BrandMark(
            state = BrandMarkState.Error(progress = 0.4f),
            modifier = Modifier.size(192.dp, 48.dp),
        )
    }
}

@Preview(name = "BrandMark — Disconnected (Dark)")
@Composable
private fun PreviewBrandMarkDisconnectedDark() {
    PreviewSurface(darkTheme = true) { BrandMark(BrandMarkState.Disconnected) }
}
