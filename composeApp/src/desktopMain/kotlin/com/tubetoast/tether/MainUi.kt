package com.tubetoast.tether

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.tubetoast.tether.di.DefaultDesktopAppConfig
import com.tubetoast.tether.di.DesktopAppContainer
import com.tubetoast.tether.presentation.DeviceListComponent
import org.jetbrains.compose.resources.painterResource
import tether.composeapp.generated.resources.Res
import tether.composeapp.generated.resources.icon

fun main() {
    val deviceName = defaultDesktopDeviceName()
    val container = DesktopAppContainer(
        DefaultDesktopAppConfig(
            deviceName = deviceName,
            port = 0,
        ),
    )
    container.startBackendOrFail(deviceName)
    container.registerShutdownHook()

    val lifecycle = LifecycleRegistry()
    val component = DeviceListComponent(
        componentContext = DefaultComponentContext(lifecycle),
        discovery = container.mdnsDiscovery,
    )
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
            App(component)
        }
    }
}
