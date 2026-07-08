package com.tubetoast.tether.network

import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.FakeFileSource
import com.tubetoast.tether.transfer.PeerUnreachableException
import com.tubetoast.tether.transfer.toPeerIdentity
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class ScriptedEndBatchFileClient(
    private val results: MutableList<Boolean>,
) : FileClient(HttpClient(MockEngine { respond("", HttpStatusCode.OK) })) {
    var callCount = 0
        private set

    override suspend fun endBatch(target: Device, batchId: String): Boolean {
        callCount++
        return if (results.size > 1) results.removeAt(0) else results.first()
    }
}

class PeerFileSenderTest {
    private val device = Device(name = "test-peer", host = "127.0.0.1", port = 9090, fingerprint = "fp-test")

    private fun TestScope.clientWith(status: HttpStatusCode, body: String): FileClient =
        FileClient(
            client = HttpClient(MockEngine) {
                install(ContentNegotiation) { json() }
                engine {
                    dispatcher = StandardTestDispatcher(testScheduler)
                    addHandler { request ->
                        val ch = (request.body as OutgoingContent.ReadChannelContent).readFrom()
                        val buf = ByteArray(8 * 1024)
                        while (!ch.isClosedForRead) ch.readAvailable(buf)
                        respond(
                            content = body,
                            status = status,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
            },
        )

    @Test
    fun `send throws PeerUnreachableException when peer not in store`() = runTest {
        val store = DiscoveredDevicesStore()
        val sender = PeerFileSender(clientWith(HttpStatusCode.OK, """{"savedPath":"/s"}"""), store)
        val source = FakeFileSource(name = "file.txt", sizeBytes = 4L)

        assertFailsWith<PeerUnreachableException> {
            sender.send(device.toPeerIdentity(), source) { _, _ -> }
        }
    }

    @Test
    fun `send completes normally and closes source on 200 OK`() = runTest {
        val store = DiscoveredDevicesStore().also { it.upsert(device) }
        val sender = PeerFileSender(clientWith(HttpStatusCode.OK, """{"savedPath":"/s"}"""), store)
        val source = FakeFileSource(name = "file.txt", sizeBytes = 4L)

        sender.send(device.toPeerIdentity(), source) { _, _ -> }

        assertTrue(source.closeCalled, "source.close() must be called after successful send")
    }

    @Test
    fun `send transmits relativePath as wire name so folder nesting is preserved`() = runTest {
        val store = DiscoveredDevicesStore().also { it.upsert(device) }
        var capturedName: String? = null
        val client = FileClient(
            client = HttpClient(MockEngine) {
                install(ContentNegotiation) { json() }
                engine {
                    dispatcher = StandardTestDispatcher(testScheduler)
                    addHandler { request ->
                        capturedName = request.url.parameters["name"]
                        val ch = (request.body as OutgoingContent.ReadChannelContent).readFrom()
                        val buf = ByteArray(8 * 1024)
                        while (!ch.isClosedForRead) ch.readAvailable(buf)
                        respond(
                            content = """{"savedPath":"/s"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
            },
        )
        val source = FakeFileSource(name = "file.txt", sizeBytes = 4L, relativePath = "photos/trip/file.txt")

        PeerFileSender(client, store).send(device.toPeerIdentity(), source) { _, _ -> }

        assertEquals("photos/trip/file.txt", capturedName, "wire name must carry the nested relativePath, not the leaf")
    }

    @Test
    fun `send throws PeerUnreachableException and still closes source on non-OK status`() = runTest {
        val store = DiscoveredDevicesStore().also { it.upsert(device) }
        val sender = PeerFileSender(
            clientWith(HttpStatusCode.InternalServerError, """{"error":"write failed"}"""),
            store,
        )
        val source = FakeFileSource(name = "file.txt", sizeBytes = 4L)

        assertFailsWith<PeerUnreachableException> {
            sender.send(device.toPeerIdentity(), source) { _, _ -> }
        }
        assertTrue(source.closeCalled, "source.close() must be called even when send fails")
    }

    @Test
    fun `send resolves to updated host-port when peer reappears with same fingerprint on new port`() = runTest {
        val oldDevice = Device(name = "test-peer", host = "127.0.0.1", port = 9090, fingerprint = "fp-test")
        val newDevice = Device(name = "test-peer", host = "127.0.0.1", port = 9091, fingerprint = "fp-test")
        val store = DiscoveredDevicesStore().also {
            it.upsert(oldDevice)
            it.upsert(newDevice)
        }
        var capturedPort: Int? = null
        val client = FileClient(
            client = HttpClient(MockEngine) {
                install(ContentNegotiation) { json() }
                engine {
                    dispatcher = StandardTestDispatcher(testScheduler)
                    addHandler { request ->
                        capturedPort = request.url.port
                        val ch = (request.body as OutgoingContent.ReadChannelContent).readFrom()
                        val buf = ByteArray(8 * 1024)
                        while (!ch.isClosedForRead) ch.readAvailable(buf)
                        respond(
                            content = """{"savedPath":"/s"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
            },
        )
        val source = FakeFileSource(name = "file.txt", sizeBytes = 4L)

        PeerFileSender(client, store).send(oldDevice.toPeerIdentity(), source) { _, _ -> }

        assertEquals(9091, capturedPort, "sender must use the new port from the store, not the old one")
    }

    @Test
    fun `endBatch retries until the receiver accepts`() = runTest {
        val store = DiscoveredDevicesStore().also { it.upsert(device) }
        val client = ScriptedEndBatchFileClient(mutableListOf(false, false, true))

        PeerFileSender(client, store).endBatch(device.toPeerIdentity(), "batch-1")

        assertEquals(3, client.callCount, "endBatch must retry until the receiver accepts")
    }

    @Test
    fun `endBatch gives up after exhausting retries without throwing`() = runTest {
        val store = DiscoveredDevicesStore().also { it.upsert(device) }
        val client = ScriptedEndBatchFileClient(mutableListOf(false))

        PeerFileSender(client, store).endBatch(device.toPeerIdentity(), "batch-1")

        assertEquals(5, client.callCount, "endBatch must exhaust its retry budget (1 initial + 4 retries)")
    }
}
