package com.tubetoast.tether

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.tubetoast.tether.config.DeviceNameStore
import com.tubetoast.tether.config.EphemeralDeviceNamePersistence
import com.tubetoast.tether.di.CliAppContainer
import com.tubetoast.tether.di.DefaultDesktopAppConfig
import com.tubetoast.tether.identity.EphemeralFingerprintPersistence
import com.tubetoast.tether.logging.initCliLogging
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.JvmPathFileSource
import com.tubetoast.tether.transfer.PeerTransferEngine
import com.tubetoast.tether.transfer.PeerTransferEngineRegistry
import com.tubetoast.tether.transfer.PeerTransferState
import com.tubetoast.tether.transfer.toPeerIdentity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.seconds

sealed class CliBatchResult {
    data object AllSent : CliBatchResult()

    data object Partial : CliBatchResult()

    data object Failed : CliBatchResult()

    data object Cancelled : CliBatchResult()
}

fun CliBatchResult.toExitCode(): Int = when (this) {
    CliBatchResult.AllSent -> 0
    CliBatchResult.Partial -> 1
    CliBatchResult.Failed -> 2
    CliBatchResult.Cancelled -> 130
}

private fun PeerTransferState.isTerminal(): Boolean = this is PeerTransferState.Sent ||
    this is PeerTransferState.Error ||
    this is PeerTransferState.Cancelled

class TetherCommand :
    CliktCommand(
        name = "tether",
        help = "Tether debug runner — local peer-to-peer file transfer over WiFi",
        epilog = buildString {
            append("Environment:\n```\n")
            append("TETHER_LOG_DEBUG=true       Show subsystem logs (off by default).\n")
            append("-Dtether.log.debug=true     Same, via JVM system property.\n")
        },
    ) {
    private val nameOverride by option("--name", help = "Device name advertised via mDNS (persisted)")

    private val port by option("--port", help = "Ktor server port (0 = pick random free port)")
        .int()

    private val configDir by option(
        "--config-dir",
        help = "Persist device identity (name + fingerprint) in this directory so it survives restart. " +
            "Default: ephemeral per-process identity.",
    ).file()

    override fun run() = runBlocking {
        initCliLogging()

        val activeEngineRef = AtomicReference<PeerTransferEngine?>(null)

        val dir = configDir
        val container = CliAppContainer(
            if (dir != null) {
                if (!isUsableConfigDir(dir)) {
                    echo("ERROR: --config-dir is not a writable directory: ${dir.absolutePath}", err = true)
                    throw ProgramResult(1)
                }
                DefaultDesktopAppConfig(port = port ?: 0, configDir = dir)
            } else {
                DefaultDesktopAppConfig(
                    port = port ?: 0,
                    namePersistenceOverride = EphemeralDeviceNamePersistence(),
                    fingerprintPersistenceOverride = EphemeralFingerprintPersistence(),
                )
            },
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

        registerShutdownHook(handle) {
            activeEngineRef.get()?.onCancel()
        }

        val discovery = container.mdnsDiscovery
        launch {
            discovery.discoveredDevices
                .map { peersIds(it) }
                .distinctUntilChanged()
                .collect { ids -> echo("[peers] $ids") }
        }

        val cliScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        echo("Commands: send <peer-name> <path> [path2 ...], retry <peer-name>, list, name <new-name>, quit")
        echo("  Tip: use quotes for names/paths with spaces — send \"My Peer\" \"/my path/file.txt\"")

        var lastExit = 0
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
                        echo("usage: send <peer-name> <path> [path2 ...]")
                        continue
                    }
                    val result = handleSend(
                        engineRegistry = container.peerTransferEngineRegistry,
                        peers = discovery.discoveredDevices.value,
                        peerName = tokens[1],
                        paths = tokens.drop(2).map { Path.of(it) },
                        onActiveEngine = { activeEngineRef.set(it) },
                    )
                    lastExit = result.toExitCode()
                }
                "retry" -> {
                    if (tokens.size < 2) {
                        echo("usage: retry <peer-name>")
                        continue
                    }
                    val result = handleRetry(
                        engineRegistry = container.peerTransferEngineRegistry,
                        peers = discovery.discoveredDevices.value,
                        peerName = tokens[1],
                        onActiveEngine = { activeEngineRef.set(it) },
                    )
                    lastExit = result.toExitCode()
                }
                "quit" -> running = false
                else -> echo("unknown command: '${tokens[0]}'. Available: send, retry, list, name, quit.")
            }
        }
        throw ProgramResult(lastExit)
    }
}

suspend fun handleSend(
    engineRegistry: PeerTransferEngineRegistry,
    peers: List<Device>,
    peerName: String,
    paths: List<Path>,
    output: (String) -> Unit = ::println,
    errorOutput: (String) -> Unit = System.err::println,
    onActiveEngine: (PeerTransferEngine?) -> Unit = {},
): CliBatchResult {
    val matching = peers.filter { it.name == peerName }
    if (matching.isEmpty()) {
        output("[send] ERROR: peer '$peerName' not found. Use 'list' to see known peers.")
        return CliBatchResult.Failed
    }
    if (matching.size > 1) {
        errorOutput("WARN: multiple peers named '$peerName', using first")
    }
    val peer = matching.first()

    val missing = paths.filter { !it.exists() }
    if (missing.isNotEmpty()) {
        missing.forEach { output("[send] ERROR: file not found: $it") }
        return CliBatchResult.Failed
    }

    val sources = paths.map { JvmPathFileSource(it) }
    val engine = engineRegistry.engineFor(peer.toPeerIdentity())
    // A fresh `send` discards any prior terminal state; without this the engine sits in
    // Sent/Error/Cancelled and startOutbound silently no-ops. `retry` reads the terminal
    // *before* this path runs and so is unaffected.
    if (engine.state.value.isTerminal()) engine.onDismiss()
    onActiveEngine(engine)
    return try {
        runEngineAndRender(engine, sources, output, errorOutput) { engine.startOutbound(it) }
    } finally {
        onActiveEngine(null)
    }
}

suspend fun handleRetry(
    engineRegistry: PeerTransferEngineRegistry,
    peers: List<Device>,
    peerName: String,
    output: (String) -> Unit = ::println,
    errorOutput: (String) -> Unit = System.err::println,
    onActiveEngine: (PeerTransferEngine?) -> Unit = {},
): CliBatchResult {
    val matching = peers.filter { it.name == peerName }
    if (matching.isEmpty()) {
        output("[retry] ERROR: peer '$peerName' not found. Use 'list' to see known peers.")
        return CliBatchResult.Failed
    }
    if (matching.size > 1) {
        errorOutput("WARN: multiple peers named '$peerName', using first")
    }
    val peer = matching.first()
    val engine = engineRegistry.engineFor(peer.toPeerIdentity())

    val stateBefore = engine.state.value
    if (!stateBefore.isTerminal()) {
        output("[retry] '$peerName' has no terminal state to retry from")
        return CliBatchResult.AllSent
    }

    // Subscribe before triggering the retry so no transition is missed between the call and the collect.
    val next = coroutineScope {
        val observer = async { engine.state.first { it !== stateBefore } }
        engine.onRetryOutbound()
        withTimeoutOrNull(2.seconds) { observer.await() }.also { observer.cancel() }
    }
    if (next == null || next.isTerminal()) {
        output("[retry] nothing to retry on '$peerName'")
        return CliBatchResult.AllSent
    }

    onActiveEngine(engine)
    return try {
        awaitAndRenderTerminal(engine, output, errorOutput)
    } finally {
        onActiveEngine(null)
    }
}

private suspend fun runEngineAndRender(
    engine: PeerTransferEngine,
    sources: List<com.tubetoast.tether.transfer.FileSource>,
    output: (String) -> Unit,
    errorOutput: (String) -> Unit,
    startFn: (List<com.tubetoast.tether.transfer.FileSource>) -> Unit,
): CliBatchResult {
    startFn(sources)
    return collectUntilTerminal(engine, output, errorOutput)
}

private suspend fun awaitAndRenderTerminal(
    engine: PeerTransferEngine,
    output: (String) -> Unit,
    errorOutput: (String) -> Unit,
): CliBatchResult = collectUntilTerminal(engine, output, errorOutput)

// `state.first { terminal }` returning before the separate renderer observed the terminal would
// drop the final "done/error/cancelled" line — this collapses both into one collector.
private suspend fun collectUntilTerminal(
    engine: PeerTransferEngine,
    output: (String) -> Unit,
    errorOutput: (String) -> Unit,
): CliBatchResult = coroutineScope {
    val terminalSeen = CompletableDeferred<PeerTransferState>()
    val rendererJob = launch {
        var lastState: PeerTransferState? = null
        engine.state.collect { state ->
            renderStateTransition(lastState, state, output, errorOutput)
            lastState = state
            if (state.isTerminal()) terminalSeen.complete(state)
        }
    }
    val terminal = terminalSeen.await()
    rendererJob.cancel()
    terminalToResult(terminal)
}

private fun renderStateTransition(
    prev: PeerTransferState?,
    current: PeerTransferState,
    output: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER") errorOutput: (String) -> Unit,
) {
    when (current) {
        is PeerTransferState.Reconnecting -> {
            output("[send] reconnecting… ${current.remainingSeconds}s remaining")
        }
        is PeerTransferState.ActiveOutbound.Preparing,
        is PeerTransferState.ActiveOutbound.Sending,
        -> {
            val prevPerFile = (prev as? PeerTransferState.ActiveOutbound)?.perFile ?: emptyList()
            val perFile = (current as PeerTransferState.ActiveOutbound).perFile
            perFile.forEachIndexed { i, status ->
                val prevStatus = prevPerFile.getOrNull(i)
                if (prevStatus == null || prevStatus::class != status::class) {
                    output("[send] ${status.name}  ${status.label()}")
                }
            }
        }
        is PeerTransferState.Sent -> {
            if (current.partialReason != null) {
                output("[send] partial — ${current.sent}/${current.total} sent")
            } else {
                output("[send] done — ${current.sent}/${current.total} sent")
            }
        }
        is PeerTransferState.Error -> {
            output("[send] error — ${current.reason}")
        }
        is PeerTransferState.Cancelled -> {
            output("[send] cancelled — ${current.sent} sent")
        }
        else -> Unit
    }
}

private fun com.tubetoast.tether.transfer.PerFileStatus.label(): String = when (this) {
    is com.tubetoast.tether.transfer.PerFileStatus.Queued -> "queued"
    is com.tubetoast.tether.transfer.PerFileStatus.InProgress -> "in progress"
    is com.tubetoast.tether.transfer.PerFileStatus.Done -> "done"
    is com.tubetoast.tether.transfer.PerFileStatus.Failed -> "failed: $reason"
}

private fun terminalToResult(state: PeerTransferState): CliBatchResult = when (state) {
    is PeerTransferState.Sent -> if (state.partialReason == null && state.sent == state.total) {
        CliBatchResult.AllSent
    } else {
        CliBatchResult.Partial
    }
    is PeerTransferState.Error -> CliBatchResult.Failed
    is PeerTransferState.Cancelled -> CliBatchResult.Cancelled
    else -> CliBatchResult.Failed
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

fun peersIds(peers: List<Device>): String =
    if (peers.isEmpty()) "none" else peers.joinToString(", ") { it.id }

fun isUsableConfigDir(dir: java.io.File): Boolean =
    (dir.mkdirs() || dir.isDirectory) && dir.canWrite()

fun main(args: Array<String>) = TetherCommand().main(args)
