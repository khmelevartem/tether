package com.tubetoast.tether

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
import com.tubetoast.tether.presentation.RootContent
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.painterResource
import tether.composeapp.generated.resources.Res
import tether.composeapp.generated.resources.icon

fun main() = runBlocking {
    initTetherLogging(debugEnabled = isDebugEnabled())
    val container = DesktopAppContainer(
        DefaultDesktopAppConfig(port = 0),
    )
    container.nameStore.init()
    val handle = container.startBackendOrFail()
    registerShutdownHook(handle)

    val lifecycle = LifecycleRegistry()
    val component = container.rootComponentFactory.create(DefaultComponentContext(lifecycle))
    lifecycle.resume()

    application {
        Window(
            onCloseRequest = {
                lifecycle.destroy()
                exitApplication()
            },
            title = "Tether",
            icon = painterResource(Res.drawable.icon),
        ) {
            RootContent(component)
        }
    }
}
