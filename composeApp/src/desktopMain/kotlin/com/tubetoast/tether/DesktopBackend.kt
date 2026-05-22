package com.tubetoast.tether

import com.tubetoast.tether.di.DesktopAppContainer
import com.tubetoast.tether.discovery.republishOnNameChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

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

internal fun DesktopAppContainer.startBackendOrFail(): Pair<Int, Job> {
    val backendScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val deviceName = runBlocking { nameStore.name.first() }
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
    val republishJob = backendScope.republishOnNameChange(nameStore, mdnsDiscovery)
    return port to republishJob
}

internal fun DesktopAppContainer.registerShutdownHook(republishJob: Job) {
    Runtime.getRuntime().addShutdownHook(
        Thread {
            val cleanup = Thread {
                runCatching { republishJob.cancel() }.onFailure {
                    System.err.println("WARN: republishJob cancel failed — ${it.message}")
                }
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
