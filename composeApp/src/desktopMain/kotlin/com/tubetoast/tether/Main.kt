package com.tubetoast.tether

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.network.FileClient
import com.tubetoast.tether.network.FileServer
import com.tubetoast.tether.network.send
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.SendResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

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
        try {
            discovery.start(deviceName, actualPort)
        } catch (e: Exception) {
            echo("ERROR: Could not start mDNS — ${e.message}", err = true)
            server.stop()
            throw ProgramResult(1)
        }
        echo("mDNS started → advertising '$deviceName' on port $actualPort\n")

        val peersJob = launch {
            discovery.discoveredDevices.collect { peers ->
                if (peers.isEmpty()) {
                    echo("[peers] none")
                } else {
                    peers.forEach { echo("[peers] $it") }
                }
            }
        }

        val fileClient = FileClient()

        // Shutdown hook handles cleanup for both Ctrl+C and normal exit via `quit`.
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
                try {
                    fileClient.close()
                } catch (e: Exception) {
                    System.err.println("WARN: FileClient close failed — ${e.message}")
                }
            },
        )

        echo("Commands: send <peer-name> <path>, list, quit")

        var running = true
        while (running) {
            val line = withContext(Dispatchers.IO) { readLine() } ?: break
            val tokens = line.trim().split("\\s+".toRegex(), limit = 3)
            when (tokens.firstOrNull()?.lowercase()) {
                "", null -> continue
                "list" -> {
                    val peers = discovery.discoveredDevices.value
                    if (peers.isEmpty()) {
                        echo("[list] no peers found")
                    } else {
                        peers.forEach { echo("[list] ${it.name}  (${it.host}:${it.port})") }
                    }
                }
                "send" -> {
                    if (tokens.size < 3) {
                        echo("usage: send <peer-name> <path>")
                        continue
                    }
                    handleSend(
                        client = fileClient,
                        peers = discovery.discoveredDevices.value,
                        peerName = tokens[1],
                        rawPath = tokens[2],
                    )
                }
                "quit" -> {
                    peersJob.cancel()
                    running = false
                }
                else -> echo("unknown command: '${tokens[0]}'. Available: send, list, quit.")
            }
        }
    }
}

internal suspend fun handleSend(
    client: FileClient,
    peers: List<Device>,
    peerName: String,
    rawPath: String,
    output: (String) -> Unit = ::println,
    progressOutput: (String) -> Unit = { s ->
        print(s)
        System.out.flush()
    },
) {
    val file = Path.of(rawPath)
    if (!file.exists()) {
        output("[send] ERROR: file not found: $rawPath")
        return
    }

    val matching = peers.filter { it.name == peerName }
    if (matching.isEmpty()) {
        output("[send] ERROR: peer '$peerName' not found. Use 'list' to see known peers.")
        return
    }
    if (matching.size > 1) {
        System.err.println("WARN: multiple peers named '$peerName', using first")
    }
    val peer = matching.first()

    val clock = TimeSource.Monotonic
    val started = clock.markNow()
    var lastPrint = started
    var lastBytes = 0L

    val result = client.send(peer, file) { transferred, total ->
        val now = clock.markNow()
        if ((now - lastPrint) >= 500.milliseconds) {
            val intervalSec = (now - lastPrint).inWholeMilliseconds / 1000.0
            val speed = if (intervalSec > 0) (transferred - lastBytes) / intervalSec else 0.0
            val totalStr = if (total > 0) " / ${formatBytes(total)}" else ""
            progressOutput("\r[send] ${formatBytes(transferred)}$totalStr  (${formatBytes(speed.toLong())}/s)   ")
            lastPrint = now
            lastBytes = transferred
        }
    }

    progressOutput("\n") // end progress line
    val elapsed = started.elapsedNow()
    when (result) {
        is SendResult.Success -> output("[send] OK — ${elapsed.inWholeMilliseconds} ms  →  ${result.savedPath}")
        is SendResult.Failure -> output("[send] FAIL: ${result.reason}")
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes < 0 -> "? B"
    bytes < 1_024 -> "$bytes B"
    bytes < 1_024 * 1_024 -> "%.1f KB".format(bytes / 1_024.0)
    bytes < 1_024 * 1_024 * 1_024 -> "%.1f MB".format(bytes / (1_024.0 * 1_024))
    else -> "%.2f GB".format(bytes / (1_024.0 * 1_024 * 1_024))
}

fun main(args: Array<String>) = TetherCommand().main(args)
