@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.tubetoast.tether

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.tubetoast.tether.di.DefaultDesktopAppConfig
import com.tubetoast.tether.di.DesktopAppContainer
import com.tubetoast.tether.logging.initTetherLogging
import com.tubetoast.tether.logging.isDebugEnabled
import com.tubetoast.tether.presentation.RootScreen
import com.tubetoast.tether.transfer.WindowDropHandler
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.painterResource
import tether.composeapp.generated.resources.Res
import tether.composeapp.generated.resources.icon

fun main() = runBlocking {
    // see docs/knowledge/desktop-system-theme.md — must be set before any Swing/AWT class loads
    System.setProperty("apple.awt.application.appearance", "system")
    initTetherLogging(debugEnabled = isDebugEnabled())
    val container = DesktopAppContainer(
        DefaultDesktopAppConfig(port = 0),
    )
    container.nameStore.init()
    val handle = container.startBackendOrFail()
    container.autoSendDispatcher.start()
    registerShutdownHook(handle)

    val lifecycle = LifecycleRegistry()
    val component = container.rootComponentFactory.create(DefaultComponentContext(lifecycle))
    lifecycle.resume()

    application {
        ObservedSystemTheme {
            Window(
                onCloseRequest = {
                    lifecycle.destroy()
                    exitApplication()
                },
                title = "Tether",
                icon = painterResource(Res.drawable.icon),
            ) {
                val scope = rememberCoroutineScope()

                LaunchedEffect(window) {
                    container.windowHolder.window = window
                }

                val dropHandler = remember(component) { WindowDropHandler(component, scope) }

                RootScreen(
                    component = component,
                    modifier = Modifier.dragAndDropTarget(
                        shouldStartDragAndDrop = { true },
                        target = dropHandler.target,
                    ),
                )
            }
        }
    }
}
