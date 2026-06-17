package com.tubetoast.tether

import com.tubetoast.tether.di.DesktopAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.warn
import ru.pocketbyte.kydra.log.wrapper.withTag

private val log = KydraLog.withTag(default = "DesktopBackend")

internal sealed class BackendStartException(
    message: String,
    cause: Throwable,
) : Exception(message, cause) {
    class FileServer(
        cause: Throwable,
    ) : BackendStartException("FileServer start failed: ${cause.message}", cause)

    class Mdns(
        cause: Throwable,
    ) : BackendStartException("mDNS start failed: ${cause.message}", cause)
}

internal class BackendHandle internal constructor(
    val port: Int,
    private val onClose: () -> Unit,
) {
    fun close() = onClose()
}

internal suspend fun DesktopAppContainer.startBackendOrFail(): BackendHandle {
    val deviceName = nameStore.name.first()
    val server = fileServer
    val port = try {
        server.start()
    } catch (e: Exception) {
        throw BackendStartException.FileServer(e)
    }
    val backendScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
        mdnsDiscovery.start(deviceName, port)
    } catch (e: Exception) {
        backendScope.cancel()
        runCatching { server.stop() }
        throw BackendStartException.Mdns(e)
    }
    nameRepublisher.start(backendScope)
    rendezvousAnnouncer.start(backendScope)
    discoveredDevicesStore.start(backendScope)
    return BackendHandle(port) {
        runCatching { nameRepublisher.stop() }.onFailure {
            log.warn { "nameRepublisher stop failed — ${it.message}" }
        }
        runCatching { rendezvousAnnouncer.stop() }.onFailure {
            log.warn { "rendezvousAnnouncer stop failed — ${it.message}" }
        }
        runCatching { discoveredDevicesStore.stop() }.onFailure {
            log.warn { "discoveredDevicesStore stop failed — ${it.message}" }
        }
        runCatching { backendScope.cancel() }
        runCatching { runBlocking { mdnsDiscovery.stop() } }.onFailure {
            log.warn { "mDNS stop failed — ${it.message}" }
        }
        runCatching { server.stop() }.onFailure {
            log.warn { "FileServer stop failed — ${it.message}" }
        }
        runCatching { fileClient.close() }.onFailure {
            log.warn { "FileClient close failed — ${it.message}" }
        }
    }
}

internal fun registerShutdownHook(handle: BackendHandle, onCancel: () -> Unit = {}, afterClose: () -> Unit = {}) {
    Runtime.getRuntime().addShutdownHook(
        Thread {
            runCatching { onCancel() }
            val cleanup = Thread {
                runCatching { handle.close() }.onFailure {
                    log.warn { "backend cleanup failed — ${it.message}" }
                }
                runCatching { afterClose() }
            }
            cleanup.isDaemon = true
            cleanup.start()
            cleanup.join(2_000)
        },
    )
}
