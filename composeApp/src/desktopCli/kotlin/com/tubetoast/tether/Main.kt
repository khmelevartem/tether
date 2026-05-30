package com.tubetoast.tether

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.tubetoast.tether.config.DeviceNameStore
import com.tubetoast.tether.config.EphemeralDeviceNamePersistence
import com.tubetoast.tether.di.DefaultDesktopAppConfig
import com.tubetoast.tether.di.DesktopAppContainer
import com.tubetoast.tether.logging.initTetherLogging
import com.tubetoast.tether.logging.isDebugEnabled
import com.tubetoast.tether.network.FileClient
import com.tubetoast.tether.network.send
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.DeviceType
import com.tubetoast.tether.protocol.SendResult
import com.tubetoast.tether.transfer.ByteFormatting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private const val ESC = ""

class TetherCommand :
    CliktCommand(
        name = "tether",
        help = "Tether debug runner — local peer-to-peer file transfer over WiFi",
        epilog = buildString {
            append("Environment:\n```\n")
            append("TETHER_LOG_DEBUG=true       Enable DEBUG-level logs.\n")
            append("-Dtether.log.debug=true     Same, via JVM system property.\n")
        },
    ) {
    private val nameOverride by option("--name", help = "Device name advertised via mDNS (persisted)")

    private val port by option("--port", help = "Ktor server port (0 = pick random free port)")
        .int()

    override fun run() = runBlocking {
        initTetherLogging(debugEnabled = isDebugEnabled())
        val container = DesktopAppContainer(
            DefaultDesktopAppConfig(port = port ?: 0, namePersistenceOverride = EphemeralDeviceNamePersistence()),
            ownDeviceType = DeviceType.Cli,
        )

        container.nameStore.init()
        nameOverride?.let { name ->
            container.nameStore.setName(name).getOrElse { e ->
                echo("ERROR: invalid --name: ${e.message}", err = true)
                throw ProgramResult(1)
            }
        }
        val deviceName = container.nameStore.name.first()

        echo("=== Tether debug runner ===")
        echo("device : $deviceName")

        val handle = try {
            container.startBackendOrFail()
        } catch (e: BackendStartException.FileServer) {
            echo("ERROR: Could not start FileServer on port ${port ?: 0} — ${e.cause?.message}", err = true)
            echo("Tip: use --port 0 to auto-select a free port.", err = true)
            throw ProgramResult(1)
        } catch (e: BackendStartException.Mdns) {
            echo("ERROR: Could not start mDNS — ${e.cause?.message}", err = true)
            throw ProgramResult(1)
        }
        echo("port   : ${handle.port}")
        echo("FileServer started  →  http://localhost:${handle.port}/health")
        echo("mDNS started → advertising '$deviceName' on port ${handle.port}\n")

        registerShutdownHook(handle)

        val discovery = container.mdnsDiscovery
        var peersLinePrinted = false
        launch {
            discovery.discoveredDevices.collect { peers ->
                val ids = if (peers.isEmpty()) "none" else peers.joinToString(", ") { it.id }
                if (peersLinePrinted) {
                    print("$ESC[1A\r$ESC[K[peers] $ids\n")
                } else {
                    print("[peers] $ids\n")
                    peersLinePrinted = true
                }
                System.out.flush()
            }
        }

        val fileClient = container.fileClient
        val cliScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        echo("Commands: send <peer-name> <path>, list, name <new-name>, quit")
        echo("  Tip: use quotes for names/paths with spaces — send \"My Peer\" \"/my path/file.txt\"")

        var running = true
        while (running) {
            val line = try {
                withContext(Dispatchers.IO) { readLine() }
            } catch (_: java.nio.charset.MalformedInputException) {
                continue
            } ?: break
            val tokens = parseTokens(line.trim())
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
                "name" -> {
                    val arg = tokens.drop(1).joinToString(" ")
                    cliScope.launch { handleName(container.nameStore, arg) }
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
                        file = Path.of(tokens[2]),
                    )
                }
                "quit" -> running = false
                else -> echo("unknown command: '${tokens[0]}'. Available: send, list, name, quit.")
            }
        }
        System.exit(0)
    }
}

suspend fun handleName(
    nameStore: DeviceNameStore,
    arg: String,
    output: (String) -> Unit = ::println,
) {
    nameStore.setName(arg).fold(
        onSuccess = { name -> output("OK name=$name") },
        onFailure = { output("ERR ${it.message}") },
    )
}

suspend fun handleSend(
    client: FileClient,
    peers: List<Device>,
    peerName: String,
    file: Path,
    output: (String) -> Unit = ::println,
    errorOutput: (String) -> Unit = System.err::println,
    progressOutput: (String) -> Unit = { text ->
        print(text)
        System.out.flush()
    },
) {
    if (!file.exists()) {
        output("[send] ERROR: file not found: $file")
        return
    }

    val matching = peers.filter { it.name == peerName }
    if (matching.isEmpty()) {
        output("[send] ERROR: peer '$peerName' not found. Use 'list' to see known peers.")
        return
    }
    if (matching.size > 1) {
        errorOutput("WARN: multiple peers named '$peerName', using first")
    }
    val peer = matching.first()

    val started = TimeSource.Monotonic.markNow()
    var lastPrint = started
    var lastBytes = 0L

    val result = client.send(peer, file) { transferred, total ->
        val now = TimeSource.Monotonic.markNow()
        if ((now - lastPrint) >= 500.milliseconds) {
            val intervalSec = (now - lastPrint).inWholeMilliseconds / 1000.0
            val speed = if (intervalSec > 0) (transferred - lastBytes) / intervalSec else 0.0
            val formattedTotal = total?.let { " / ${ByteFormatting.formatSize(it)}" } ?: ""
            progressOutput(
                "\r[send] ${ByteFormatting.formatSize(
                    transferred,
                )}$formattedTotal  (${ByteFormatting.formatSize(speed.toLong())}/s)   ",
            )
            lastPrint = now
            lastBytes = transferred
        }
    }

    progressOutput("\n")
    val elapsed = started.elapsedNow()
    when (result) {
        is SendResult.Success -> output("[send] OK — ${elapsed.inWholeMilliseconds} ms  →  ${result.savedPath}")
        is SendResult.Failure -> output("[send] FAIL: ${result.reason}")
    }
}

fun parseTokens(line: String): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var i = 0
    while (i < line.length) {
        when (val ch = line[i]) {
            '"', '\'' -> {
                i++
                while (i < line.length && line[i] != ch) {
                    if (line[i] == '\\' && i + 1 < line.length) {
                        current.append(line[++i])
                    } else {
                        current.append(line[i])
                    }
                    i++
                }
            }
            '\\' -> if (i + 1 < line.length) current.append(line[++i])
            ' ', '\t' -> {
                if (current.isNotEmpty()) {
                    tokens.add(current.toString())
                    current.clear()
                }
            }
            else -> current.append(ch)
        }
        i++
    }
    if (current.isNotEmpty()) tokens.add(current.toString())
    return tokens
}

fun main(args: Array<String>) = TetherCommand().main(args)
