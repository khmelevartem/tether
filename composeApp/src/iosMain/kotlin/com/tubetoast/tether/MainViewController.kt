package com.tubetoast.tether

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.ComposeUIViewController
import com.tubetoast.tether.discovery.MdnsDiscovery
import platform.UIKit.UIDevice

@Suppress("ktlint:standard:function-naming")
fun MainViewController() = ComposeUIViewController {
    val discovery = MdnsDiscovery()
    DisposableEffect(Unit) {
        val deviceName = UIDevice.currentDevice.name
        // TODO: replace with actual FileServer port once server is available on iOS
        discovery.start(deviceName, port = 8080)
        onDispose { discovery.stop() }
    }
    App()
}
