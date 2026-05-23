package com.tubetoast.tether.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState

@Composable
fun RootContent(component: RootComponent, modifier: Modifier = Modifier) {
    MaterialTheme {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .safeContentPadding(),
        ) {
            val stack by component.stack.subscribeAsState()
            Children(stack = stack) { child ->
                when (val instance = child.instance) {
                    is RootComponent.Child.DeviceListChild -> DeviceListScreen(instance.component)
                    is RootComponent.Child.TransferDetailsChild -> TransferDetailsScreen(instance.component)
                }
            }
        }
    }
}

@Composable
fun TransferDetailsScreen(component: TransferDetailsComponent) {
}
