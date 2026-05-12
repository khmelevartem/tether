package com.tubetoast.tether

import com.tubetoast.tether.di.DesktopAppContainer

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

internal fun defaultDesktopDeviceName(): String =
    "Tether-${System.getenv("USER") ?: "dev"}"

internal fun DesktopAppContainer.startBackendOrFail(deviceName: String): Int {
    val server = fileServer
    val port = try {
        server.start()
    } catch (e: Exception) {
        throw BackendStartException.FileServer(e)
    }
    try {
        mdnsDiscovery.start(deviceName, port)
    } catch (e: Exception) {
        runCatching { server.stop() }
        throw BackendStartException.Mdns(e)
    }
    return port
}

internal fun DesktopAppContainer.registerShutdownHook() {
    Runtime.getRuntime().addShutdownHook(
        Thread {
            val cleanup = Thread {
                runCatching { mdnsDiscovery.stop() }.onFailure {
                    System.err.println("WARN: mDNS stop failed — ${it.message}")
                }
                runCatching { fileServer.stop() }.onFailure {
                    System.err.println("WARN: FileServer stop failed — ${it.message}")
                }
                runCatching { fileClient.close() }.onFailure {
                    System.err.println("WARN: FileClient close failed — ${it.message}")
                }
            }
            cleanup.isDaemon = true
            cleanup.start()
            cleanup.join(2_000)
        },
    )
}
