package com.tubetoast.tether.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.ui.components.BrandMark
import com.tubetoast.tether.ui.components.BrandMarkState
import com.tubetoast.tether.ui.preview.PreviewFixtures
import com.tubetoast.tether.ui.preview.PreviewSurface
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun DeviceListScreen(component: DeviceListComponent, modifier: Modifier = Modifier) {
    val state by component.state.subscribeAsState()
    DeviceListContent(
        devices = state.devices,
        onDeviceClick = { /* TODO: navigate to file selection — #191 */ },
        modifier = modifier,
    )
}

@Composable
private fun DeviceListContent(
    devices: List<Device>,
    onDeviceClick: (Device) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = TetherTheme.spacing
    val colors = TetherTheme.colors
    val typography = TetherTheme.typography
    val shapes = TetherTheme.shapes

    if (devices.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BrandMark(state = BrandMarkState.Searching)
            // TODO: move to resources after an approach is picked — #100
            BasicText(
                text = "Ищем устройства в сети…",
                style = typography.bodyMedium.copy(color = colors.textMuted),
                modifier = Modifier.padding(top = spacing.lg),
            )
        }
    } else {
        LazyColumn(modifier = modifier.fillMaxSize()) {
            items(devices, key = { it.id }) { device ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.lg, vertical = spacing.sm)
                        .clip(shapes.md)
                        .background(colors.surfaceRaised)
                        .border(width = spacing.borderWidth, color = colors.border, shape = shapes.md)
                        .clickable { onDeviceClick(device) }
                        .padding(horizontal = spacing.lg, vertical = spacing.lg),
                ) {
                    Column {
                        BasicText(
                            text = device.name,
                            style = typography.titleMedium.copy(color = colors.textPrimary),
                        )
                        BasicText(
                            text = "${device.host}:${device.port}",
                            style = typography.bodyMedium.copy(color = colors.textMuted),
                            modifier = Modifier.padding(top = spacing.xs),
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Discovering — empty")
@Composable
private fun PreviewDiscovering() {
    PreviewSurface {
        DeviceListContent(
            devices = PreviewFixtures.emptyDevices,
            onDeviceClick = {},
        )
    }
}

@Preview(name = "Single device")
@Composable
private fun PreviewSingleDevice() {
    PreviewSurface {
        DeviceListContent(
            devices = PreviewFixtures.singleDevice,
            onDeviceClick = {},
        )
    }
}

@Preview(name = "Multiple devices")
@Composable
private fun PreviewMultipleDevices() {
    PreviewSurface {
        DeviceListContent(
            devices = PreviewFixtures.multipleDevices,
            onDeviceClick = {},
        )
    }
}

@Preview(name = "Multiple devices — dark")
@Composable
private fun PreviewMultipleDevicesDark() {
    PreviewSurface(darkTheme = true) {
        DeviceListContent(
            devices = PreviewFixtures.multipleDevices,
            onDeviceClick = {},
        )
    }
}
