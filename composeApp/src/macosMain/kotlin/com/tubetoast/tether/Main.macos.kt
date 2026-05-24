package com.tubetoast.tether

import com.tubetoast.tether.di.DefaultMacosAppConfig
import com.tubetoast.tether.di.MacosAppContainer
import com.tubetoast.tether.logging.initLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import platform.CoreFoundation.CFRunLoopRun

fun main() {
    initLogging()
    val container = MacosAppContainer(DefaultMacosAppConfig())
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    scope.launch {
        container.nameStore.init()
        val name = container.nameStore.name.first()
        val port = container.fileServer.start()
        container.mdnsDiscovery.start(name, port = port)
        container.nameRepublisher.start(scope)
    }
    // NSNetService callbacks are dispatched via the run loop — without this the process exits before discovery fires.
    CFRunLoopRun()
}
