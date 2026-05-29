package com.tubetoast.tether.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.tubetoast.tether.presentation.banners.BannersSection
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun RootContent(component: RootComponent, modifier: Modifier = Modifier) {
    TetherTheme {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(TetherTheme.colors.surface)
                .safeContentPadding(),
        ) {
            val stack by component.stack.subscribeAsState()

            Column(modifier = Modifier.fillMaxSize()) {
                BannersSection(
                    component = component.bannersComponent,
                    modifier = Modifier.fillMaxWidth(),
                )
                Children(stack = stack) { child ->
                    when (val instance = child.instance) {
                        is RootComponent.Child.PeerListChild ->
                            PeerListScreen(instance.component)
                        is RootComponent.Child.TransferDetailsChild ->
                            TransferDetailsScreen(instance.component)
                    }
                }
            }
        }
    }
}
