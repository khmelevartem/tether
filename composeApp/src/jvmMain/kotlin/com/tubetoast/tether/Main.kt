package com.tubetoast.tether

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.network.FileServer
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException

class TetherCommand :
    CliktCommand(
        name = "tether",
        help = "Tether debug runner — local peer-to-peer file transfer over WiFi",
    ) {
    private val deviceName by option("--name", help = "Device name advertised via mDNS")
        .default("Tether-${System.getenv("USER") ?: "dev"}")

    private val port by option("--port", help = "Ktor server port (0 = pick random free port)")
        .int()
        .default(0)

    override fun run() = runBlocking {
        echo("=== Tether debug runner ===")
        echo("device : $deviceName")

        val server = FileServer(port)
        val actualPort = try {
            server.start()
        } catch (e: IOException) {
            echo("ERROR: Could not start FileServer on port $port — ${e.message}", err = true)
            echo("Tip: use --port 0 to auto-select a free port.", err = true)
            throw ProgramResult(1)
        }
        echo("port   : $actualPort")
        echo("FileServer started  →  http://localhost:$actualPort/health")

        val discovery = MdnsDiscovery()
        discovery.start(deviceName, actualPort)
        echo("mDNS started  →  advertising '$deviceName' on port $actualPort\n")

        launch {
            discovery.discoveredDevices.collect { peers ->
                if (peers.isEmpty()) {
                    echo("[peers] none")
                } else {
                    peers.forEach { echo("[peers] $it") }
                }
            }
        }

        Runtime.getRuntime().addShutdownHook(
            Thread {
                try {
                    discovery.stop()
                } catch (e: Exception) {
                    System.err.println("WARN: mDNS stop failed — ${e.message}")
                }
                try {
                    server.stop()
                } catch (e: Exception) {
                    System.err.println("WARN: FileServer stop failed — ${e.message}")
                }
            },
        )

        echo("Ctrl+C to stop")
        awaitCancellation()
    }
}

fun main(args: Array<String>) = TetherCommand().main(args)
