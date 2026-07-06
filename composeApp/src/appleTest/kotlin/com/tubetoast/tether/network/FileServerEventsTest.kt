@file:OptIn(ExperimentalForeignApi::class)

package com.tubetoast.tether.network

import com.tubetoast.tether.TempDirs
import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.preferences.TempDataStore
import com.tubetoast.tether.protocol.BatchBeginRequest
import com.tubetoast.tether.protocol.BatchCancelRequest
import com.tubetoast.tether.protocol.BatchEndRequest
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.PeerIdentity
import com.tubetoast.tether.security.DefaultTrustedDeviceStore
import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.InMemoryKeychainStore
import com.tubetoast.tether.transfer.InboundEvent
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
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
    private val tempDirs = TempDirs(slug = "tether-fs-ev")
    private val cleanupTempStores = mutableListOf<TempDataStore>()
    private var startedServer: FileServer? = null

    @AfterTest
    fun cleanup() {
        startedServer?.stop()
        startedServer = null
        tempDirs.cleanup()
        cleanupTempStores.forEach { it.tearDown() }
        cleanupTempStores.clear()
    }

    private val peerHost = "127.0.0.1"
    private val peer = PeerIdentity("peer-fp")

    private fun newServerWithPeer(): Pair<FileServer, Int> {
        val temp = TempDataStore().also { cleanupTempStores += it }
        val downloadsDir = tempDirs.newDir()
        val store = DiscoveredDevicesStore()
        store.upsert(Device(name = "TestPeer", host = peerHost, port = 9999, fingerprint = "peer-fp"))
        val server = FileServer(
            configuredPort = 0,
            uploadStorage = FileUploadStorage(
                root = downloadsDir,
                backend = AppleUploadStorageBackend(downloadsDir),
            ),
            trustedDeviceStore = DefaultTrustedDeviceStore(temp.dataStore),
            deviceKeyPair = DeviceKeyPair(keychain = InMemoryKeychainStore()),
            discoveredDevicesStore = store,
        )
        startedServer = server
        return server to server.start()
    }

    @Test
    fun successful_upload_emits_FileStarted_Progress_and_FileCompleted() {
        val (server, port) = newServerWithPeer()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val collected = mutableListOf<InboundEvent>()
                val collectionJob = launch {
                    server.events.collect { event ->
                        collected += event
                        if (event is InboundEvent.FileCompleted) return@collect
                    }
                }
                withTimeout(5.seconds) {
                    client.post("http://localhost:$port/upload?name=hello.txt") {
                        contentType(ContentType.Application.OctetStream)
                        setBody("hello apple events".encodeToByteArray())
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
    fun cancelInbound_during_upload_emits_ConnectionLost_with_cancelled_true() {
        val (server, port) = newServerWithPeer()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val collected = mutableListOf<InboundEvent>()
                val collectionJob = launch {
                    server.events.collect { collected += it }
                }
                val uploadJob = launch {
                    try {
                        client.post("http://localhost:$port/upload?name=cancel.bin") {
                            setBody(SlowEventsContent(totalBytes = 8L * 1024 * 1024))
                        }
                    } catch (_: Exception) {
                    }
                }
                withTimeout(5.seconds) {
                    while (collected.none { it is InboundEvent.FileStarted }) delay(10.milliseconds)
                }
                server.cancelInbound(peer)
                withTimeout(5.seconds) {
                    while (collected.none { it is InboundEvent.ConnectionLost }) delay(10.milliseconds)
                }
                uploadJob.cancel()
                collectionJob.cancel()

                val lostEvent = collected.filterIsInstance<InboundEvent.ConnectionLost>().first()
                assertEquals(peer, lostEvent.peer)
                assertTrue(lostEvent.cancelled, "ConnectionLost.cancelled must be true on explicit cancel")
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun batch_begin_with_valid_body_emits_BatchStarted_with_correct_batchId_and_totalFiles() {
        val (server, port) = newServerWithPeer()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val collected = mutableListOf<InboundEvent>()
                val collectionJob = launch { server.events.collect { collected += it } }
                val response: HttpResponse = withTimeout(5.seconds) {
                    client.post("http://localhost:$port/batch-begin") {
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
    fun batch_begin_with_totalFiles_less_than_1_returns_400() {
        val (_, port) = newServerWithPeer()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val response: HttpResponse = withTimeout(5.seconds) {
                    client.post("http://localhost:$port/batch-begin") {
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
    fun batch_cancel_with_valid_body_returns_200_and_emits_BatchCancelled() {
        val (server, port) = newServerWithPeer()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val collected = mutableListOf<InboundEvent>()
                val collectionJob = launch { server.events.collect { collected += it } }
                val response: HttpResponse = withTimeout(5.seconds) {
                    client.post("http://localhost:$port/batch-cancel") {
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
    fun batch_end_with_valid_body_returns_200_and_emits_BatchEnd() {
        val (server, port) = newServerWithPeer()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val collected = mutableListOf<InboundEvent>()
                val collectionJob = launch { server.events.collect { collected += it } }
                val response: HttpResponse = withTimeout(5.seconds) {
                    client.post("http://localhost:$port/batch-end") {
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
    fun batch_end_with_malformed_body_returns_400() {
        val (_, port) = newServerWithPeer()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val response: HttpResponse = withTimeout(5.seconds) {
                    client.post("http://localhost:$port/batch-end") {
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
    fun batch_cancel_with_malformed_body_returns_400() {
        val (_, port) = newServerWithPeer()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val response: HttpResponse = withTimeout(5.seconds) {
                    client.post("http://localhost:$port/batch-cancel") {
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
    fun mid_stream_client_disconnect_emits_ConnectionLost() {
        val (server, port) = newServerWithPeer()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val collected = mutableListOf<InboundEvent>()
                val collectionJob = launch {
                    server.events.collect { collected += it }
                }
                try {
                    withTimeout(150.milliseconds) {
                        client.post("http://localhost:$port/upload?name=trunc.bin") {
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
