package com.tubetoast.tether

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.ComposeUIViewController
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tubetoast.tether.di.DefaultIosAppConfig
import com.tubetoast.tether.di.IosAppContainer
import com.tubetoast.tether.presentation.RootComponent
import platform.UIKit.UIDevice

@Suppress("ktlint:standard:function-naming")
fun MainViewController() = run {
    val container = IosAppContainer(
        DefaultIosAppConfig(deviceName = UIDevice.currentDevice.name),
    )
    val lifecycle = LifecycleRegistry()
    val context = DefaultComponentContext(lifecycle)
    val root = RootComponent(
        componentContext = context,
        discovery = container.mdnsDiscovery,
        fileClient = container.fileClient,
        filePicker = null,
    )
    ComposeUIViewController {
        DisposableEffect(Unit) {
            lifecycle.resume()
            val port = container.fileServer.start()
            container.mdnsDiscovery.start(container.deviceName, port = port)
            onDispose {
                container.mdnsDiscovery.stop()
                container.fileServer.stop()
                lifecycle.stop()
                lifecycle.destroy()
            }
        }
        App(root)
    }
}
