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
import com.tubetoast.tether.logging.initLogging
import com.tubetoast.tether.presentation.RootScreen
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.error
import ru.pocketbyte.kydra.log.wrapper.withTag

private val log = KydraLog.withTag(default = "Main.iOS")

@Suppress("ktlint:standard:function-naming")
fun MainViewController() = run {
    initLogging()
    val container = IosAppContainer(DefaultIosAppConfig())
    val lifecycle = LifecycleRegistry()
    val component = container.rootComponentFactory.create(DefaultComponentContext(lifecycle))
    ComposeUIViewController {
        DisposableEffect(Unit) {
            lifecycle.resume()
            lateinit var scope: CoroutineScope
            val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
                log.error { "startup failed: ${throwable.message ?: throwable}" }
                scope.cancel()
            }
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + exceptionHandler)
            scope.launch {
                container.nameStore.init()
                val name = container.nameStore.name.first()
                val port = container.fileServer.start()
                container.mdnsDiscovery.start(name, port = port)
                container.nameRepublisher.start(scope)
                container.rendezvousAnnouncer.start(scope)
                container.autoSendDispatcher.start()
            }
            onDispose {
                container.nameRepublisher.stop()
                container.rendezvousAnnouncer.stop()
                scope.cancel()
                // scope is cancelled above; runBlocking for brief teardown.
                runBlocking { container.mdnsDiscovery.stop() }
                container.fileServer.stop()
                lifecycle.stop()
                lifecycle.destroy()
            }
        }
        RootScreen(component)
    }.also { container.iosViewControllerHolder.attach(it) }
}
