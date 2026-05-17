package com.tubetoast.tether.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.tubetoast.tether.presentation.transfer.TransferScreen
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun RootContent(root: RootComponent, modifier: Modifier = Modifier) {
    TetherTheme {
        Children(
            stack = root.stack,
            modifier = modifier.fillMaxSize(),
        ) { child ->
            when (val instance = child.instance) {
                is RootComponent.Child.DeviceListChild -> MaterialTheme {
                    DeviceListScreen(
                        component = instance.component,
                        modifier = Modifier
                            .fillMaxSize()
                            .safeContentPadding(),
                    )
                }

                is RootComponent.Child.TransferChild -> TransferScreen(
                    component = instance.component,
                    modifier = Modifier
                        .fillMaxSize()
                        .safeContentPadding(),
                )
            }
        }
    }
}
