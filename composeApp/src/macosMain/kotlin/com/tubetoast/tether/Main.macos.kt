@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.tubetoast.tether

import com.tubetoast.tether.di.DefaultMacosAppConfig
import com.tubetoast.tether.di.MacosAppContainer
import com.tubetoast.tether.logging.initLogging
import kotlinx.cinterop.staticCFunction
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import platform.CoreFoundation.CFRunLoopGetMain
import platform.CoreFoundation.CFRunLoopRun
import platform.CoreFoundation.CFRunLoopStop
import platform.posix.SIGINT
import platform.posix.SIGTERM
import platform.posix.signal
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.error
import ru.pocketbyte.kydra.log.wrapper.withTag

private val log = KydraLog.withTag(default = "Tether.Main.macOS")

fun main() {
    initLogging()
    val container = MacosAppContainer(DefaultMacosAppConfig())
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        log.error { "startup failed: ${throwable.message ?: throwable}" }
        CFRunLoopStop(CFRunLoopGetMain())
    }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + exceptionHandler)
    scope.launch {
        container.nameStore.init()
        val name = container.nameStore.name.first()
        val port = container.fileServer.start()
        container.mdnsDiscovery.start(name, port = port)
        container.nameRepublisher.start(scope)
    }

    val stopRunLoop = staticCFunction<Int, Unit> { _ -> CFRunLoopStop(CFRunLoopGetMain()) }
    signal(SIGINT, stopRunLoop)
    signal(SIGTERM, stopRunLoop)

    // NSNetService callbacks are dispatched via the run loop — without this the process exits before discovery fires.
    CFRunLoopRun()

    container.nameRepublisher.stop()
    container.mdnsDiscovery.stop()
    container.fileServer.stop()
    scope.cancel()
}
