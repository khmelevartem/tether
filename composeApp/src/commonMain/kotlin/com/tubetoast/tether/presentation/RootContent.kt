package com.tubetoast.tether.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.router.stack.ChildStack
import com.tubetoast.tether.presentation.settings.SettingsScreen
import com.tubetoast.tether.ui.theme.TetherTheme

@Composable
fun RootScreen(component: RootComponent, modifier: Modifier = Modifier) {
    val stack by component.stack.subscribeAsState()
    val isDragActive by component.dragActive.subscribeAsState()
    RootContent(stack = stack, isDragActive = isDragActive, modifier = modifier)
}

@Composable
internal fun RootContent(
    stack: ChildStack<*, RootComponent.Child>,
    isDragActive: Boolean,
    modifier: Modifier = Modifier,
) {
    TetherTheme {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(TetherTheme.colors.surface)
                .windowInsetsPadding(WindowInsets.safeContent.only(WindowInsetsSides.Vertical)),
        ) {
            Children(stack = stack) { child ->
                when (val instance = child.instance) {
                    is RootComponent.Child.PeerListChild ->
                        PeerListScreen(instance.component)
                    is RootComponent.Child.TransferDetailsChild ->
                        TransferDetailsScreen(instance.component)
                    is RootComponent.Child.SettingsChild ->
                        SettingsScreen(instance.component)
                }
            }

            if (isDragActive) DragOverlay(Modifier.fillMaxSize())
        }
    }
}
