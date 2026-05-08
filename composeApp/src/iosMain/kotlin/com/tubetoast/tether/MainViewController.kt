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
            // TODO: replace with actual FileServer port once server is available on iOS
            container.mdnsDiscovery.start(container.deviceName, port = 8080)
            onDispose { container.mdnsDiscovery.stop() }
        }
        App()
    }
}
