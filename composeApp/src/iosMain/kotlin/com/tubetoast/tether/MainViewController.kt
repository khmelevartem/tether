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
import com.tubetoast.tether.presentation.DeviceListComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Suppress("ktlint:standard:function-naming")
fun MainViewController() = run {
    val container = IosAppContainer(DefaultIosAppConfig())
    val lifecycle = LifecycleRegistry()
    val context = DefaultComponentContext(lifecycle)
    val component = DeviceListComponent(
        componentContext = context,
        discovery = container.mdnsDiscovery,
    )
    ComposeUIViewController {
        DisposableEffect(Unit) {
            lifecycle.resume()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            scope.launch {
                container.nameStore.init()
                val name = container.nameStore.name.first()
                val port = container.fileServer.start()
                container.mdnsDiscovery.start(name, port = port)
                container.nameRepublisher.start(scope)
            }
            onDispose {
                container.nameRepublisher.stop()
                scope.cancel()
                container.mdnsDiscovery.stop()
                container.fileServer.stop()
                lifecycle.stop()
                lifecycle.destroy()
            }
        }
        App(component)
    }
}
