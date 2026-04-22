package com.tubetoast.tether

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.network.FileServer
import kotlinx.coroutines.*
import java.net.ServerSocket

class TetherCommand : CliktCommand(
    name = "tether",
    help = "Tether debug runner — local peer-to-peer file transfer over WiFi"
) {
    private val deviceName by option("--name", help = "Device name advertised via mDNS")
        .default("Tether-${System.getenv("USER") ?: "dev"}")

    private val port by option("--port", help = "Ktor server port (0 = pick random free port)")
        .int()
        .default(0)

    override fun run() = runBlocking {
        val actualPort = if (port == 0) ServerSocket(0).use { it.localPort } else port

        echo("=== Tether debug runner ===")
        echo("device : $deviceName")
        echo("port   : $actualPort")

        val server = FileServer(actualPort)
        server.start()
        echo("FileServer started  →  http://localhost:$actualPort/health")

        val discovery = MdnsDiscovery()
        discovery.start(deviceName, actualPort)
        echo("mDNS started (JmDNS stub — not wired yet)\n")

        launch {
            discovery.discoveredDevices.collect { peers ->
                if (peers.isEmpty()) echo("[peers] none")
                else peers.forEach { echo("[peers] $it") }
            }
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            discovery.stop()
            server.stop()
        })

        echo("Ctrl+C to stop")
        awaitCancellation()
    }
}

fun main(args: Array<String>) = TetherCommand().main(args)
