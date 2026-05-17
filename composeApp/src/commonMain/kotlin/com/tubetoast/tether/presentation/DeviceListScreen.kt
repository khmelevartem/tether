package com.tubetoast.tether.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun DeviceListScreen(component: DeviceListComponent, modifier: Modifier = Modifier) {
    val state by component.state.subscribeAsState()

    Box(modifier = modifier.fillMaxSize()) {
        if (state.devices.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                // TODO: move to resources after an approach is picked — #100
                Text(
                    text = "Ищем устройства в сети…",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (state.pendingFiles.isNotEmpty()) {
                    item {
                        PendingFilesBanner(count = state.pendingFiles.size)
                    }
                }
                items(state.devices, key = { it.id }) { device ->
                    Card(
                        onClick = { component.onDeviceClicked(device) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                        )
                        Text(
                            text = "${device.host}:${device.port}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        )
                    }
                }
            }
        }

        if (state.isDragHover) {
            DragOverlay(
                rejected = state.dragRejected,
                modifier = Modifier.fillMaxSize(),
            )
        }

        state.sendChooserTarget?.let { target ->
            SendModeChooserDialog(
                target = target,
                onSendFiles = { component.onSendFiles(target) },
                onSendFolder = { component.onSendFolder(target) },
                onDismiss = { component.onDismissChooser() },
            )
        }
    }
}

@Composable
private fun PendingFilesBanner(count: Int) {
    val colors = TetherTheme.colors
    val typography = TetherTheme.typography
    val spacing = TetherTheme.spacing
    val shapes = TetherTheme.shapes
    val text = if (count == 1) {
        "1 file ready to send — pick a device."
    } else {
        "$count files ready to send — pick a device."
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.sm)
            .clip(shapes.md)
            .background(colors.accent.copy(alpha = 0.12f))
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        BasicText(
            text = text,
            style = typography.bodyMedium.copy(color = colors.textPrimary),
            modifier = Modifier.padding(spacing.lg),
        )
    }
}

@Composable
private fun DragOverlay(rejected: Boolean, modifier: Modifier = Modifier) {
    val colors = TetherTheme.colors
    val typography = TetherTheme.typography
    val spacing = TetherTheme.spacing
    val borderColor = colors.accent
    val overlayLabel = if (rejected) "Transfer in progress — wait to drop." else "Drop to send"
    Box(
        modifier = modifier
            .background(colors.surface.copy(alpha = 0.85f))
            .padding(spacing.xl)
            .drawBehind {
                drawRect(
                    color = borderColor,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f)),
                    ),
                )
            }.semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "Drop zone active"
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = overlayLabel,
            style = typography.titleLarge.copy(color = colors.textPrimary),
        )
    }
}
