package com.tubetoast.tether.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.tubetoast.tether.presentation.transfer.TransferScreen
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun RootContent(root: RootComponent, modifier: Modifier = Modifier) {
    TetherTheme {
        val dragRejectedOverlay by root.dragRejectedOverlay.subscribeAsState()
        Box(modifier = modifier.fillMaxSize()) {
            Children(
                stack = root.stack,
                modifier = Modifier.fillMaxSize(),
            ) { child ->
                when (val instance = child.instance) {
                    is RootComponent.Child.DeviceListChild -> DeviceListScreen(
                        component = instance.component,
                        modifier = Modifier
                            .fillMaxSize()
                            .safeContentPadding(),
                    )

                    is RootComponent.Child.TransferChild -> TransferScreen(
                        component = instance.component,
                        modifier = Modifier
                            .fillMaxSize()
                            .safeContentPadding(),
                    )
                }
            }
            if (dragRejectedOverlay) {
                TransferDragRejectedOverlay(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun TransferDragRejectedOverlay(modifier: Modifier = Modifier) {
    val colors = TetherTheme.colors
    val typography = TetherTheme.typography
    val spacing = TetherTheme.spacing
    Box(
        modifier = modifier
            .background(colors.surface.copy(alpha = 0.85f))
            .padding(spacing.xl)
            .drawBehind {
                drawRect(
                    color = colors.error,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f)),
                    ),
                )
            }.semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "Transfer in progress overlay"
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "Transfer in progress — wait to drop.",
            style = typography.titleLarge.copy(color = colors.textPrimary),
        )
    }
}
