package com.tubetoast.tether.network

import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.preferences.TempDataStore
import com.tubetoast.tether.protocol.BatchBeginRequest
import com.tubetoast.tether.protocol.BatchCancelRequest
import com.tubetoast.tether.protocol.BatchEndRequest
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.PeerIdentity
import com.tubetoast.tether.security.DefaultTrustedDeviceStore
import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.transfer.InboundCancelRegistry
import com.tubetoast.tether.transfer.InboundEvent
import com.tubetoast.tether.transfer.InboundEventBus
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// real CIO server — CIOApplicationEngine hardcodes real-thread dispatchers
@Suppress("ktlint:tether:no-run-blocking-in-tests")
class FileServerEventsTest {
    private val cleanupPaths = mutableListOf<File>()
    private val cleanupTempStores = mutableListOf<TempDataStore>()
    private var startedServer: FileServer? = null

    @AfterTest
    fun teardown() {
        startedServer?.stop()
        startedServer = null
        cleanupPaths.forEach { it.deleteRecursively() }
        cleanupPaths.clear()
        cleanupTempStores.forEach { it.tearDown() }
        cleanupTempStores.clear()
    }

    private val peerHost = "127.0.0.1"
    private val peer = PeerIdentity("peer-fp")

    private class TestServer(
        val port: Int,
        val inboundEventBus: InboundEventBus,
        val cancelRegistry: InboundCancelRegistry,
    )

    private fun newServerWithPeer(): TestServer {
        val configDir = Files.createTempDirectory("tether-fs-events-keys").toFile().also(cleanupPaths::add)
        val temp = TempDataStore().also { cleanupTempStores += it }
        val downloadsDir = Files.createTempDirectory("tether-fs-events-dl").toFile().also(cleanupPaths::add)
        val store = DiscoveredDevicesStore()
        store.upsert(Device(name = "TestPeer", host = peerHost, port = 9999, fingerprint = "peer-fp"))
        val inboundEventBus = InboundEventBus()
        val cancelRegistry = InboundCancelRegistry()
        val server = FileServer(
            configuredPort = 0,
            uploadStorage = FileUploadStorage(
                root = downloadsDir.absolutePath,
                backend = JvmUploadStorageBackend(downloadsDir.absolutePath),
            ),
            trustedDeviceStore = DefaultTrustedDeviceStore(temp.dataStore),
            deviceKeyPair = DeviceKeyPair(configDir),
            inboundEventBus = inboundEventBus,
            cancelRegistry = cancelRegistry,
            discoveredDevicesStore = store,
        )
        startedServer = server
        return TestServer(server.start(), inboundEventBus, cancelRegistry)
    }

    @Test
    fun `successful upload emits FileStarted, Progress, and FileCompleted`() {
        val testServer = newServerWithPeer()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val collected = mutableListOf<InboundEvent>()
                val collectionJob = launch {
                    testServer.inboundEventBus.events.collect { event ->
                        collected += event
                        if (event is InboundEvent.FileCompleted) return@collect
                    }
                }
                withTimeout(5.seconds) {
                    client.post("http://localhost:${testServer.port}/upload?name=hello.txt") {
                        contentType(ContentType.Application.OctetStream)
                        setBody("hello events".toByteArray())
                    }
                }
                withTimeout(2.seconds) {
                    while (collected.none { it is InboundEvent.FileCompleted }) delay(10.milliseconds)
                }
                collectionJob.cancel()

                assertTrue(collected.any { it is InboundEvent.FileStarted }, "FileStarted must be emitted")
                assertTrue(collected.any { it is InboundEvent.Progress }, "Progress must be emitted")
                assertTrue(collected.any { it is InboundEvent.FileCompleted }, "FileCompleted must be emitted")
                assertTrue(
                    collected.all { it.peer == peer },
                    "All events must carry the peer identity resolved from the store",
                )
                val completed = collected.filterIsInstance<InboundEvent.FileCompleted>().first()
                assertEquals("hello.txt", completed.name)
                assertTrue(completed.finalPath?.isNotEmpty() == true)
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `mid-stream client disconnect emits ConnectionLost`() {
        val testServer = newServerWithPeer()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val collected = mutableListOf<InboundEvent>()
                val collectionJob = launch {
                    testServer.inboundEventBus.events.collect { collected += it }
                }
                try {
                    withTimeout(150.milliseconds) {
                        client.post("http://localhost:${testServer.port}/upload?name=trunc.bin") {
                            setBody(SlowEventsContent(totalBytes = 8L * 1024 * 1024))
                        }
                    }
                    fail("expected cancellation")
                } catch (_: TimeoutCancellationException) {
                } catch (_: Exception) {
                }
                withTimeout(5.seconds) {
                    while (collected.none { it is InboundEvent.ConnectionLost }) delay(50.milliseconds)
                }
                collectionJob.cancel()

                assertTrue(
                    collected.any { it is InboundEvent.ConnectionLost },
                    "ConnectionLost must be emitted on mid-stream disconnect",
                )
                val lost = collected.filterIsInstance<InboundEvent.ConnectionLost>().first()
                assertEquals(peer, lost.peer)
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `InboundCancelRegistry request during upload emits CancelledByReceiver`() {
        val testServer = newServerWithPeer()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val collected = mutableListOf<InboundEvent>()
                val collectionJob = launch {
                    testServer.inboundEventBus.events.collect { collected += it }
                }
                val uploadJob = launch {
                    try {
                        client.post("http://localhost:${testServer.port}/upload?name=cancel.bin") {
                            setBody(SlowEventsContent(totalBytes = 8L * 1024 * 1024))
                        }
                    } catch (_: Exception) {
                    }
                }
                // Wait until the upload has started (FileStarted received)
                withTimeout(5.seconds) {
                    while (collected.none { it is InboundEvent.FileStarted }) delay(10.milliseconds)
                }
                testServer.cancelRegistry.request(peer)
                withTimeout(5.seconds) {
                    while (collected.none { it is InboundEvent.CancelledByReceiver }) delay(10.milliseconds)
                }
                uploadJob.cancel()
                collectionJob.cancel()

                val cancelledEvent = collected.filterIsInstance<InboundEvent.CancelledByReceiver>().first()
                assertEquals(peer, cancelledEvent.peer)
                assertTrue(
                    collected.none { it is InboundEvent.ConnectionLost },
                    "an explicit receiver cancel must not also emit ConnectionLost",
                )
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `batch-begin with valid body emits BatchStarted with correct batchId and totalFiles`() {
        val testServer = newServerWithPeer()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val collected = mutableListOf<InboundEvent>()
                val collectionJob = launch { testServer.inboundEventBus.events.collect { collected += it } }
                val response: HttpResponse = withTimeout(5.seconds) {
                    client.post("http://localhost:${testServer.port}/batch-begin") {
                        contentType(ContentType.Application.Json)
                        setBody(BatchBeginRequest(batchId = "test-batch-1", totalFiles = 3, totalBytes = null))
                    }
                }
                withTimeout(2.seconds) {
                    while (collected.none { it is InboundEvent.BatchStarted }) delay(10.milliseconds)
                }
                collectionJob.cancel()

                assertEquals(HttpStatusCode.OK, response.status)
                val event = collected.filterIsInstance<InboundEvent.BatchStarted>().first()
                assertEquals("test-batch-1", event.batchId)
                assertEquals(3, event.totalFiles)
                assertEquals(peer, event.peer)
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `batch-begin with totalFiles less than 1 returns 400`() {
        val testServer = newServerWithPeer()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val response: HttpResponse = withTimeout(5.seconds) {
                    client.post("http://localhost:${testServer.port}/batch-begin") {
                        contentType(ContentType.Application.Json)
                        setBody(BatchBeginRequest(batchId = "bad-batch", totalFiles = 0))
                    }
                }
                assertEquals(HttpStatusCode.BadRequest, response.status)
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `batch-cancel with valid body returns 200 and emits BatchCancelled`() {
        val testServer = newServerWithPeer()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val collected = mutableListOf<InboundEvent>()
                val collectionJob = launch { testServer.inboundEventBus.events.collect { collected += it } }
                val response: HttpResponse = withTimeout(5.seconds) {
                    client.post("http://localhost:${testServer.port}/batch-cancel") {
                        contentType(ContentType.Application.Json)
                        setBody(BatchCancelRequest(batchId = "batch-to-cancel"))
                    }
                }
                withTimeout(2.seconds) {
                    while (collected.none { it is InboundEvent.BatchCancelled }) delay(10.milliseconds)
                }
                collectionJob.cancel()

                assertEquals(HttpStatusCode.OK, response.status)
                val event = collected.filterIsInstance<InboundEvent.BatchCancelled>().first()
                assertEquals("batch-to-cancel", event.batchId)
                assertEquals(peer, event.peer)
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `batch-end with valid body returns 200 and emits BatchEnd`() {
        val testServer = newServerWithPeer()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val collected = mutableListOf<InboundEvent>()
                val collectionJob = launch { testServer.inboundEventBus.events.collect { collected += it } }
                val response: HttpResponse = withTimeout(5.seconds) {
                    client.post("http://localhost:${testServer.port}/batch-end") {
                        contentType(ContentType.Application.Json)
                        setBody(BatchEndRequest(batchId = "batch-to-end"))
                    }
                }
                withTimeout(2.seconds) {
                    while (collected.none { it is InboundEvent.BatchEnd }) delay(10.milliseconds)
                }
                collectionJob.cancel()

                assertEquals(HttpStatusCode.OK, response.status)
                val event = collected.filterIsInstance<InboundEvent.BatchEnd>().first()
                assertEquals("batch-to-end", event.batchId)
                assertEquals(peer, event.peer)
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `batch-end with malformed body returns 400`() {
        val testServer = newServerWithPeer()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val response: HttpResponse = withTimeout(5.seconds) {
                    client.post("http://localhost:${testServer.port}/batch-end") {
                        contentType(ContentType.Application.Json)
                        setBody("not-valid-json")
                    }
                }
                assertEquals(HttpStatusCode.BadRequest, response.status)
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `batch-cancel with malformed body returns 400`() {
        val testServer = newServerWithPeer()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val response: HttpResponse = withTimeout(5.seconds) {
                    client.post("http://localhost:${testServer.port}/batch-cancel") {
                        contentType(ContentType.Application.Json)
                        setBody("not-valid-json")
                    }
                }
                assertEquals(HttpStatusCode.BadRequest, response.status)
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `upload without peer in store emits no events`() {
        // Server without a discoveredDevicesStore: events flow stays empty for unknown hosts.
        val configDir = Files.createTempDirectory("tether-fs-events-keys2").toFile().also(cleanupPaths::add)
        val temp = TempDataStore().also { cleanupTempStores += it }
        val downloadsDir = Files.createTempDirectory("tether-fs-events-dl2").toFile().also(cleanupPaths::add)
        val inboundEventBus = InboundEventBus()
        val server = FileServer(
            configuredPort = 0,
            uploadStorage = FileUploadStorage(
                root = downloadsDir.absolutePath,
                backend = JvmUploadStorageBackend(downloadsDir.absolutePath),
            ),
            trustedDeviceStore = DefaultTrustedDeviceStore(temp.dataStore),
            deviceKeyPair = DeviceKeyPair(configDir),
            inboundEventBus = inboundEventBus,
            cancelRegistry = InboundCancelRegistry(),
        )
        startedServer = server
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val collected = mutableListOf<InboundEvent>()
                val collectionJob = launch { inboundEventBus.events.collect { collected += it } }
                client.post("http://localhost:$port/upload?name=x.txt") {
                    contentType(ContentType.Application.OctetStream)
                    setBody("data".toByteArray())
                }
                delay(200.milliseconds)
                collectionJob.cancel()

                assertTrue(collected.isEmpty(), "No events without a peer in the store, got: $collected")
            }
        } finally {
            client.close()
        }
    }
}

private class SlowEventsContent(
    private val totalBytes: Long,
) : OutgoingContent.WriteChannelContent() {
    override val contentLength: Long = totalBytes
    override val contentType: ContentType = ContentType.Application.OctetStream

    override suspend fun writeTo(channel: ByteWriteChannel) {
        val chunk = ByteArray(64 * 1024) { 0x42 }
        var sent = 0L
        while (sent < totalBytes) {
            val n = minOf(chunk.size.toLong(), totalBytes - sent).toInt()
            channel.writeFully(chunk, 0, n)
            sent += n
            delay(20.milliseconds)
        }
    }
}
