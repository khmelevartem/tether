package com.tubetoast.tether

import com.tubetoast.tether.network.FileClient
import com.tubetoast.tether.network.FileServer
import com.tubetoast.tether.preferences.FakePeerPreferencesStore
import com.tubetoast.tether.preferences.TempDataStore
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.SendResult
import com.tubetoast.tether.security.DefaultTrustedDeviceStore
import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.transfer.BatchSender
import com.tubetoast.tether.transfer.NoOpConnectionMonitor
import com.tubetoast.tether.transfer.PeerTransferEngine
import com.tubetoast.tether.transfer.PeerTransferEngineRegistry
import com.tubetoast.tether.transfer.PeerUnreachableException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// real CIO server — CIOApplicationEngine hardcodes real-thread dispatchers
@Suppress("ktlint:tether:no-run-blocking-in-tests")
class CliSendTest {
    private lateinit var tmpDir: File
    private lateinit var configDir: File
    private lateinit var tempStore: TempDataStore
    private lateinit var server: FileServer
    private lateinit var fileClient: FileClient
    private lateinit var device: Device
    private lateinit var engineRegistry: PeerTransferEngineRegistry

    @BeforeTest
    fun setup() {
        tmpDir = Files.createTempDirectory("tether-cli-test").toFile()
        configDir = Files.createTempDirectory("tether-cli-test-keys").toFile()
        tempStore = TempDataStore()
        server = FileServer(
            configuredPort = 0,
            downloadsDir = tmpDir,
            trustedDeviceStore = DefaultTrustedDeviceStore(tempStore.dataStore),
            deviceKeyPair = DeviceKeyPair(configDir),
        )
        val port = server.start()
        device = Device(name = "cli-test", host = "127.0.0.1", port = port)
        fileClient = FileClient.default()
        engineRegistry = buildRegistry(device, fileClient)
    }

    @AfterTest
    fun teardown() {
        fileClient.close()
        server.stop()
        tempStore.tearDown()
        tmpDir.deleteRecursively()
        configDir.deleteRecursively()
    }

    private fun buildRegistry(target: Device, client: FileClient): PeerTransferEngineRegistry {
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        return PeerTransferEngineRegistry(
            appScope = appScope,
            engineFactory = { peer, engineScope ->
                PeerTransferEngine(
                    peer = peer,
                    batchSenderFactory = {
                        BatchSender(
                            sendOne = { source, onProgress ->
                                try {
                                    when (
                                        val r = client.send(
                                            target,
                                            source.openReadChannel(),
                                            source.name,
                                            source.sizeBytes,
                                            onProgress,
                                        )
                                    ) {
                                        is SendResult.Success -> Unit
                                        is SendResult.Failure -> throw PeerUnreachableException(
                                            RuntimeException(r.reason),
                                        )
                                    }
                                } finally {
                                    source.close()
                                }
                            },
                            connectionMonitor = NoOpConnectionMonitor,
                        )
                    },
                    scope = engineScope,
                    peerPreferencesStore = FakePeerPreferencesStore(),
                )
            },
        )
    }

    @Test
    fun `send happy path transfers single file and reports AllSent`() {
        val content = "hello from cli test".toByteArray()
        val file = Files.createTempFile("cli-send", ".txt")
        file.writeBytes(content)

        val messages = mutableListOf<String>()
        val result = runBlocking {
            handleSend(
                engineRegistry = engineRegistry,
                peers = listOf(device),
                peerName = device.name,
                paths = listOf(file),
                output = messages::add,
            )
        }

        assertEquals(CliBatchResult.AllSent, result)
        val doneMsg = messages.firstOrNull { it.contains("done") }
        assertTrue(doneMsg != null, "Expected done message but got: $messages")

        Files.deleteIfExists(file)
    }

    @Test
    fun `send happy path transfers multiple files and reports AllSent`() {
        val file1 = Files.createTempFile("cli-batch-1", ".txt").also { it.writeBytes("file1 data".toByteArray()) }
        val file2 = Files.createTempFile("cli-batch-2", ".txt").also { it.writeBytes("file2 data".toByteArray()) }

        val messages = mutableListOf<String>()
        val result = runBlocking {
            handleSend(
                engineRegistry = engineRegistry,
                peers = listOf(device),
                peerName = device.name,
                paths = listOf(file1, file2),
                output = messages::add,
            )
        }

        assertEquals(CliBatchResult.AllSent, result)
        val done = messages.firstOrNull { it.contains("2/2") }
        assertTrue(done != null, "Expected 2/2 done message but got: $messages")

        // Verify both files landed in downloads dir
        val downloads = tmpDir
            .walk()
            .filter { it.isFile }
            .map { it.name }
            .toSet()
        assertTrue(downloads.contains(file1.fileName.toString()), "file1 not in downloads: $downloads")
        assertTrue(downloads.contains(file2.fileName.toString()), "file2 not in downloads: $downloads")

        Files.deleteIfExists(file1)
        Files.deleteIfExists(file2)
    }

    @Test
    fun `send reports Failed when peer not found`() {
        val file = Files.createTempFile("cli-no-peer", ".txt")
        file.writeBytes("data".toByteArray())

        val messages = mutableListOf<String>()
        val result = runBlocking {
            handleSend(
                engineRegistry = engineRegistry,
                peers = emptyList(),
                peerName = "nonexistent",
                paths = listOf(file),
                output = messages::add,
            )
        }

        assertEquals(CliBatchResult.Failed, result)
        assertTrue(
            messages.any { it.contains("peer 'nonexistent' not found") },
            "Expected peer-not-found error but got: $messages",
        )

        Files.deleteIfExists(file)
    }

    @Test
    fun `send reports Failed when file does not exist`() {
        val nonExistent = Files.createTempDirectory("cli-missing").resolve("no-such-file.bin")
        val messages = mutableListOf<String>()
        val result = runBlocking {
            handleSend(
                engineRegistry = engineRegistry,
                peers = listOf(device),
                peerName = device.name,
                paths = listOf(nonExistent),
                output = messages::add,
            )
        }

        assertEquals(CliBatchResult.Failed, result)
        assertTrue(
            messages.any { it.contains("file not found") },
            "Expected file-not-found error but got: $messages",
        )
    }

    @Test
    fun `send reports Failed when all paths are missing`() {
        val missing1 = Files.createTempDirectory("cli-m1").resolve("nope1.bin")
        val missing2 = Files.createTempDirectory("cli-m2").resolve("nope2.bin")

        val messages = mutableListOf<String>()
        val result = runBlocking {
            handleSend(
                engineRegistry = engineRegistry,
                peers = listOf(device),
                peerName = device.name,
                paths = listOf(missing1, missing2),
                output = messages::add,
            )
        }

        assertEquals(CliBatchResult.Failed, result)
        assertEquals(2, messages.count { it.contains("file not found") }, "Expected two not-found errors: $messages")
    }

    @Test
    fun `send warns and uses first when multiple peers share name`() {
        val file = Files.createTempFile("cli-dup", ".txt")
        file.writeBytes("dup-peer test".toByteArray())

        val duplicate = device.copy(host = "127.0.0.2")
        val errors = mutableListOf<String>()
        val messages = mutableListOf<String>()
        val result = runBlocking {
            handleSend(
                engineRegistry = engineRegistry,
                peers = listOf(device, duplicate),
                peerName = device.name,
                paths = listOf(file),
                output = messages::add,
                errorOutput = errors::add,
            )
        }

        assertTrue(errors.any { it.contains("multiple peers") }, "Expected WARN but got: $errors")
        assertEquals(CliBatchResult.AllSent, result)
        assertFalse(messages.any { it.contains("error") }, "Unexpected error: $messages")

        Files.deleteIfExists(file)
    }

    @Test
    fun `retry after partial sends only the failed file`() {
        val realFile = Files.createTempFile("cli-retry-real", ".txt")
        realFile.writeBytes("real content".toByteArray())
        val realFile2 = Files.createTempFile("cli-retry-real2", ".txt")
        realFile2.writeBytes("real content 2".toByteArray())

        val callCount = AtomicInteger(0)
        val (partialRegistry, _) = buildPartialRegistry(device, fileClient, callCount, failOnCall = 2)

        // First send — 2 files, second fails inside engine
        val partialMessages = mutableListOf<String>()
        val partialResult = runBlocking {
            handleSend(
                engineRegistry = partialRegistry,
                peers = listOf(device),
                peerName = device.name,
                paths = listOf(realFile, realFile2),
                output = partialMessages::add,
            )
        }
        // callCount == 2: both files attempted, second failed
        assertEquals(2, callCount.get(), "Expected 2 send attempts on first batch: $callCount")

        // After partial, retry — should attempt only the failed file (realFile2)
        val retryMessages = mutableListOf<String>()
        val retryResult = runBlocking {
            handleRetry(
                engineRegistry = partialRegistry,
                peers = listOf(device),
                peerName = device.name,
                output = retryMessages::add,
            )
        }

        assertEquals(CliBatchResult.AllSent, retryResult, "Retry should complete successfully: $retryMessages")
        // callCount == 3: first batch 2 calls + retry 1 call (only the failed file)
        assertEquals(3, callCount.get(), "Retry should attempt only the 1 failed file, total calls=3: $callCount")

        // Both files should be in downloads dir after retry
        val downloads = tmpDir
            .walk()
            .filter { it.isFile }
            .map { it.name }
            .toSet()
        assertTrue(
            downloads.contains(realFile.fileName.toString()),
            "realFile not in downloads after retry: $downloads",
        )
        assertTrue(
            downloads.contains(realFile2.fileName.toString()),
            "realFile2 not in downloads after retry: $downloads",
        )

        Files.deleteIfExists(realFile)
        Files.deleteIfExists(realFile2)
    }

    @Test
    fun `send returns Partial when one file fails mid-batch`() {
        val file1 = Files.createTempFile("cli-partial-1", ".txt").also { it.writeBytes("data1".toByteArray()) }
        val file2 = Files.createTempFile("cli-partial-2", ".txt").also { it.writeBytes("data2".toByteArray()) }

        val callCount = AtomicInteger(0)
        val (partialRegistry, _) = buildPartialRegistry(device, fileClient, callCount, failOnCall = 2)

        val messages = mutableListOf<String>()
        val result = runBlocking {
            handleSend(
                engineRegistry = partialRegistry,
                peers = listOf(device),
                peerName = device.name,
                paths = listOf(file1, file2),
                output = messages::add,
            )
        }

        assertEquals(CliBatchResult.Partial, result, "Expected Partial but got: $result — $messages")
        assertEquals(1, result.toExitCode(), "Partial exit code must be 1")

        Files.deleteIfExists(file1)
        Files.deleteIfExists(file2)
    }

    @Test
    fun `send returns Cancelled when engine is cancelled mid-flight`() {
        val file = Files.createTempFile("cli-cancel", ".txt").also { it.writeBytes("data".toByteArray()) }

        var capturedEngine: PeerTransferEngine? = null
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val cancelRegistry = PeerTransferEngineRegistry(
            appScope = appScope,
            engineFactory = { peer, engineScope ->
                PeerTransferEngine(
                    peer = peer,
                    batchSenderFactory = {
                        BatchSender(
                            sendOne = { _, _ -> awaitCancellation() },
                            connectionMonitor = NoOpConnectionMonitor,
                        )
                    },
                    scope = engineScope,
                    peerPreferencesStore = FakePeerPreferencesStore(),
                )
            },
        )

        val messages = mutableListOf<String>()
        val result = runBlocking {
            var sendResult: CliBatchResult? = null
            val sendJob = launch {
                sendResult = handleSend(
                    engineRegistry = cancelRegistry,
                    peers = listOf(device),
                    peerName = device.name,
                    paths = listOf(file),
                    output = messages::add,
                    onActiveEngine = { capturedEngine = it },
                )
            }
            while (capturedEngine == null) delay(10)
            delay(50)
            capturedEngine!!.onCancel()
            sendJob.join()
            sendResult
        }

        assertEquals(CliBatchResult.Cancelled, result, "Expected Cancelled result")
        assertEquals(130, CliBatchResult.Cancelled.toExitCode(), "Cancelled exit code must be 130")
        assertTrue(
            messages.any { it.contains("cancelled") },
            "Expected cancelled message but got: $messages",
        )

        Files.deleteIfExists(file)
    }

    @Test
    fun `collectUntilTerminal does not return prematurely on Reconnecting state`() {
        val file = Files.createTempFile("cli-reconnect", ".txt").also { it.writeBytes("data".toByteArray()) }

        // sendOne blocks on sendStarted until the test emits a drop; after reconnect it succeeds.
        val sendStarted = CompletableDeferred<Unit>()
        val dropFlow = kotlinx.coroutines.flow.MutableSharedFlow<com.tubetoast.tether.transfer.ConnectionDrop>(
            extraBufferCapacity = 1,
        )
        val reconnectMonitor = object : com.tubetoast.tether.transfer.ConnectionMonitor {
            override val drops = dropFlow

            override suspend fun awaitReconnect(timeout: kotlin.time.Duration): Boolean = true
        }

        var sendCallCount = 0
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val reconnectRegistry = PeerTransferEngineRegistry(
            appScope = appScope,
            engineFactory = { peer, engineScope ->
                PeerTransferEngine(
                    peer = peer,
                    batchSenderFactory = {
                        BatchSender(
                            sendOne = { source, onProgress ->
                                sendCallCount++
                                if (sendCallCount == 1) {
                                    // Signal that send is in flight, then block until drop cancels us
                                    sendStarted.complete(Unit)
                                    awaitCancellation()
                                }
                                try {
                                    when (
                                        val r = fileClient.send(
                                            device,
                                            source.openReadChannel(),
                                            source.name,
                                            source.sizeBytes,
                                            onProgress,
                                        )
                                    ) {
                                        is SendResult.Success -> Unit
                                        is SendResult.Failure -> throw PeerUnreachableException(
                                            RuntimeException(r.reason),
                                        )
                                    }
                                } finally {
                                    source.close()
                                }
                            },
                            connectionMonitor = reconnectMonitor,
                        )
                    },
                    scope = engineScope,
                    peerPreferencesStore = FakePeerPreferencesStore(),
                )
            },
        )

        val messages = mutableListOf<String>()
        runBlocking {
            val sendJob = launch {
                handleSend(
                    engineRegistry = reconnectRegistry,
                    peers = listOf(device),
                    peerName = device.name,
                    paths = listOf(file),
                    output = messages::add,
                )
            }
            // Wait until the first sendOne is blocking, then emit a drop
            sendStarted.await()
            dropFlow.emit(com.tubetoast.tether.transfer.ConnectionDrop)
            sendJob.join()
        }

        // Reconnecting message must appear (non-terminal state was rendered, not skipped)
        assertTrue(
            messages.any { it.contains("reconnecting") },
            "Expected reconnecting message but got: $messages",
        )
        // Terminal done message must also appear (collectUntilTerminal didn't return prematurely)
        assertTrue(
            messages.any { it.contains("done") },
            "Expected done message after reconnect but got: $messages",
        )

        Files.deleteIfExists(file)
    }

    @Test
    fun `retry on peer with no terminal state reports has no terminal state to retry from`() {
        val messages = mutableListOf<String>()
        val result = runBlocking {
            handleRetry(
                engineRegistry = engineRegistry,
                peers = listOf(device),
                peerName = device.name,
                output = messages::add,
            )
        }

        assertTrue(
            messages.any { it.contains("has no terminal state to retry from") },
            "Expected 'has no terminal state to retry from' but got: $messages",
        )
        assertEquals(CliBatchResult.AllSent, result)
    }

    /** Returns a registry and the external call counter. `failOnCall` = which call number to throw on (1-based). */
    private fun buildPartialRegistry(
        target: Device,
        client: FileClient,
        callCount: AtomicInteger,
        failOnCall: Int,
    ): Pair<PeerTransferEngineRegistry, AtomicInteger> {
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val registry = PeerTransferEngineRegistry(
            appScope = appScope,
            engineFactory = { peer, engineScope ->
                PeerTransferEngine(
                    peer = peer,
                    batchSenderFactory = {
                        BatchSender(
                            sendOne = { source, onProgress ->
                                val n = callCount.incrementAndGet()
                                if (n == failOnCall) {
                                    throw PeerUnreachableException(RuntimeException("forced failure on call $n"))
                                }
                                try {
                                    when (
                                        val r = client.send(
                                            target,
                                            source.openReadChannel(),
                                            source.name,
                                            source.sizeBytes,
                                            onProgress,
                                        )
                                    ) {
                                        is SendResult.Success -> Unit
                                        is SendResult.Failure -> throw PeerUnreachableException(
                                            RuntimeException(r.reason),
                                        )
                                    }
                                } finally {
                                    source.close()
                                }
                            },
                            connectionMonitor = NoOpConnectionMonitor,
                        )
                    },
                    scope = engineScope,
                    peerPreferencesStore = FakePeerPreferencesStore(),
                )
            },
        )
        return registry to callCount
    }
}
