package com.tubetoast.tether

import com.tubetoast.tether.di.DesktopAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first

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
    return BackendHandle(port) {
        runCatching { nameRepublisher.stop() }.onFailure {
            System.err.println("WARN: nameRepublisher stop failed — ${it.message}")
        }
        runCatching { backendScope.cancel() }
        runCatching { mdnsDiscovery.stop() }.onFailure {
            System.err.println("WARN: mDNS stop failed — ${it.message}")
        }
        runCatching { server.stop() }.onFailure {
            System.err.println("WARN: FileServer stop failed — ${it.message}")
        }
        runCatching { fileClient.close() }.onFailure {
            System.err.println("WARN: FileClient close failed — ${it.message}")
        }
    }
}

internal fun registerShutdownHook(handle: BackendHandle) {
    Runtime.getRuntime().addShutdownHook(
        Thread {
            val cleanup = Thread {
                runCatching { handle.close() }.onFailure {
                    System.err.println("WARN: backend cleanup failed — ${it.message}")
                }
            }
            cleanup.isDaemon = true
            cleanup.start()
            cleanup.join(2_000)
        },
    )
}
