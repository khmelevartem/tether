package com.tubetoast.tether

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.ComposeUIViewController
import com.tubetoast.tether.di.DefaultIosAppConfig
import com.tubetoast.tether.di.IosAppContainer
import platform.UIKit.UIDevice

@Suppress("ktlint:standard:function-naming")
fun MainViewController() = run {
    val container = IosAppContainer(
        DefaultIosAppConfig(deviceName = UIDevice.currentDevice.name),
    )
    ComposeUIViewController {
        DisposableEffect(Unit) {
            val port = container.fileServer.start()
            container.mdnsDiscovery.start(container.deviceName, port = port)
            onDispose {
                container.mdnsDiscovery.stop()
                container.fileServer.stop()
            }
        }
        App()
    }
}
