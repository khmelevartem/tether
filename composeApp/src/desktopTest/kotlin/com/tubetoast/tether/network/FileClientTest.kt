package com.tubetoast.tether.network

import com.tubetoast.tether.PairingTestFixtures
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.PairResponse
import com.tubetoast.tether.protocol.SendResult
import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore
import com.tubetoast.tether.security.deviceIdFromPublicKey
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class FileClientTest {
    private val device = Device(name = "test", host = "127.0.0.1", port = 8080)
    private val fixtures = PairingTestFixtures()

    private fun TestScope.mockClient(
        noProgressTimeout: kotlin.time.Duration = 60.seconds,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): FileClient = FileClient(
        client = HttpClient(MockEngine) {
            install(ContentNegotiation) { json() }
            engine {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler(handler)
            }
        },
        noProgressTimeout = noProgressTimeout,
    )

    private fun okResponse(savedPath: String) = """{"savedPath":"$savedPath"}"""

    @Test
    fun `send returns Success with saved path`() = runTest {
        val client = mockClient { request ->
            val body = request.body as OutgoingContent.ReadChannelContent
            val ch = body.readFrom()
            val buf = ByteArray(8 * 1024)
            while (!ch.isClosedForRead) ch.readAvailable(buf)
            respond(
                content = okResponse("/saved/path.txt"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val file = Files.createTempFile("send-test", ".txt")
        file.writeBytes("hello from client".toByteArray())
        try {
            val result = client.send(device, file)
            assertIs<SendResult.Success>(result)
            assertTrue(result.savedPath.endsWith(".txt"))
        } finally {
            client.close()
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `send throws FileNotFoundException for missing file`() = runTest {
        val client = mockClient { error("should not be called") }
        val missing = Files.createTempDirectory("gone").resolve("no-such-file.bin")
        try {
            assertFailsWith<FileNotFoundException> {
                client.send(device, missing)
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `send preserves file content exactly`() = runTest {
        val captured = AtomicReference<ByteArray>()
        val client = mockClient { request ->
            val body = request.body as OutgoingContent.ReadChannelContent
            val ch = body.readFrom()
            val out = mutableListOf<Byte>()
            val buf = ByteArray(8 * 1024)
            while (!ch.isClosedForRead) {
                val n = ch.readAvailable(buf)
                if (n > 0) out.addAll(buf.take(n))
            }
            captured.set(out.toByteArray())
            respond(
                content = okResponse("/saved/file.bin"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val payload = ByteArray(256) { it.toByte() }
        val file = Files.createTempFile("binary-test", ".bin")
        file.writeBytes(payload)
        try {
            client.send(device, file)
            assertTrue(
                payload.contentEquals(captured.get()),
                "Captured content does not match sent content",
            )
        } finally {
            client.close()
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `send sets Content-Length and omits chunked transfer encoding when totalBytes known`() = runTest {
        data class Captured(
            val contentLength: Long?,
            val transferEncoding: String?,
        )

        val captured = AtomicReference<Captured>()
        val client = mockClient { request ->
            val body = request.body as OutgoingContent.ReadChannelContent
            val ch = body.readFrom()
            val buf = ByteArray(8 * 1024)
            while (!ch.isClosedForRead) ch.readAvailable(buf)
            captured.set(
                Captured(
                    contentLength = request.body.contentLength,
                    transferEncoding = request.headers[HttpHeaders.TransferEncoding],
                ),
            )
            respond(
                content = okResponse("ignored"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val payload = ByteArray(2048) { (it % 251).toByte() }
        val file = Files.createTempFile("content-length-test", ".bin")
        file.writeBytes(payload)
        try {
            client.send(device, file)
            val c = captured.get() ?: error("handler did not capture request")
            assertTrue(
                c.contentLength == payload.size.toLong(),
                "Content-Length must equal file size",
            )
            assertNull(c.transferEncoding, "Transfer-Encoding must be absent (no chunked)")
        } finally {
            client.close()
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `send returns Failure when server is not running`() = runTest {
        val client = mockClient { throw IOException("connection refused") }
        val file = Files.createTempFile("no-server", ".txt")
        file.writeBytes("data".toByteArray())
        try {
            val result = client.send(device, file)
            assertIs<SendResult.Failure>(result)
        } finally {
            client.close()
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `send invokes onProgress callback with monotonically increasing bytesTransferred`() = runTest {
        val client = mockClient { request ->
            val body = request.body as OutgoingContent.ReadChannelContent
            val ch = body.readFrom()
            val buf = ByteArray(8 * 1024)
            while (!ch.isClosedForRead) ch.readAvailable(buf)
            respond(
                content = okResponse("/saved/file.bin"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val observed = mutableListOf<Long>()
        val payload = ByteArray(64 * 1024) { it.toByte() }
        val file = Files.createTempFile("progress-test", ".bin")
        file.writeBytes(payload)
        try {
            val result = client.send(device, file) { sent, _ -> observed.add(sent) }
            assertIs<SendResult.Success>(result)
            assertTrue(observed.isNotEmpty(), "onProgress must fire at least once")
            assertTrue(
                observed.last() == payload.size.toLong(),
                "final progress should equal payload size, got ${observed.last()}",
            )
            assertTrue(
                observed == observed.sorted(),
                "progress values must be monotonically increasing, got $observed",
            )
        } finally {
            client.close()
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `send does not trip watchdog during cold-start before first byte is sent`() = runTest {
        val client = mockClient(noProgressTimeout = 500.milliseconds) {
            delay(Long.MAX_VALUE)
            error("never")
        }
        val source = ByteChannel(autoFlush = true)
        val sendJob = launch {
            withTimeout(10.seconds) {
                client.send(
                    device = device,
                    channel = source,
                    fileName = "cold-start.bin",
                    totalBytes = null,
                )
            }
        }
        advanceTimeBy(5.seconds)
        assertTrue(sendJob.isActive, "watchdog must not fire before first byte is sent")
        source.cancel(null)
        client.close()
        sendJob.cancel()
    }

    @Test
    fun `send returns Failure when source channel cancels mid-stream`() = runTest {
        val client = mockClient { request ->
            val body = request.body as OutgoingContent.ReadChannelContent
            val ch = body.readFrom()
            val buf = ByteArray(8 * 1024)
            while (!ch.isClosedForRead) ch.readAvailable(buf)
            ch.closedCause?.let { throw it }
            respond(
                content = okResponse("ignored"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val partialChannel = ByteChannel(autoFlush = true)
        launch {
            partialChannel.writeFully(ByteArray(4 * 1024) { 0x42 })
            partialChannel.cancel(IOException("simulated mid-upload disconnect"))
        }
        try {
            val result = client.send(
                device = device,
                channel = partialChannel,
                fileName = "trunc.bin",
                totalBytes = 64L * 1024,
                onProgress = null,
            )
            assertIs<SendResult.Failure>(result)
        } finally {
            client.close()
        }
    }

    @Test
    fun `send returns Failure when no upload progress for the configured timeout`() = runTest {
        val client = mockClient(noProgressTimeout = 2.seconds) {
            delay(Long.MAX_VALUE)
            error("never")
        }
        val source = ByteChannel(autoFlush = true)
        launch { source.writeFully(ByteArray(100) { 0x42 }) }
        try {
            val result = client.send(
                device = device,
                channel = source,
                fileName = "watchdog.bin",
                totalBytes = null,
            )
            assertIs<SendResult.Failure>(result)
        } finally {
            source.cancel(null)
            client.close()
        }
    }

    private fun TestScope.httpFor(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient = HttpClient(MockEngine) {
        install(ContentNegotiation) { json() }
        engine {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler(handler)
        }
    }

    private fun TestScope.pairingClient(
        pairHandler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
        uploadHandler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
        trustedDeviceStore: TrustedDeviceStore = fixtures.newTrustedStore(),
        keyPair: DeviceKeyPair = DeviceKeyPair(fixtures.newConfigDir()),
        confirmationHandler: PairingConfirmationHandler = PairingConfirmationHandler { _, _ -> true },
    ): FileClient {
        // FileClient and HandshakePairing share one client, exactly as the containers wire them.
        val client = httpFor { request ->
            when {
                request.url.segments.last() == "pair" -> pairHandler(request)
                else -> uploadHandler(request)
            }
        }
        return FileClient(
            client = client,
            pairing = HandshakePairing(
                client = client,
                trustedDeviceStore = trustedDeviceStore,
                ownKeyPair = keyPair,
                ownDeviceName = { "TestDevice" },
                confirmationHandler = confirmationHandler,
            ),
        )
    }

    @Test
    fun `send skips ensurePaired when pairing not configured`() = runTest {
        val client = mockClient { request ->
            respond(
                content = okResponse("/saved/file.txt"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val file = Files.createTempFile("pair-skip-test", ".txt")
        file.writeBytes("data".toByteArray())
        try {
            val result = client.send(device, file)
            assertIs<SendResult.Success>(result)
        } finally {
            client.close()
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `send calls pair endpoint before upload`() = runTest {
        val serverKey = byteArrayOf(7, 8, 9)
        val store = fixtures.newTrustedStore()
        val uploadCalled = AtomicBoolean(false)
        val client = pairingClient(
            pairHandler = {
                respond(
                    content = Json.encodeToString(PairResponse(serverKey)),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
            uploadHandler = {
                uploadCalled.set(true)
                respond(
                    content = okResponse("/saved/file.txt"),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
            trustedDeviceStore = store,
        )
        val file = Files.createTempFile("pair-before-upload", ".txt")
        file.writeBytes("data".toByteArray())
        try {
            val result = client.send(device, file)
            assertIs<SendResult.Success>(result)
            assertTrue(uploadCalled.get(), "upload must be called after successful pairing")
            val deviceId = deviceIdFromPublicKey(serverKey)
            assertTrue(store.isTrusted(deviceId), "peer key must be saved to trust store after successful pairing")
        } finally {
            client.close()
            Files.deleteIfExists(file)
            fixtures.tearDown()
        }
    }

    @Test
    fun `send returns Failure when pair returns 403`() = runTest {
        val uploadCalled = AtomicBoolean(false)
        val client = pairingClient(
            pairHandler = {
                respond(
                    content = """{"error":"pairing_rejected"}""",
                    status = HttpStatusCode.Forbidden,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
            uploadHandler = {
                uploadCalled.set(true)
                respond(content = "", status = HttpStatusCode.OK)
            },
        )
        val file = Files.createTempFile("pair-403-test", ".txt")
        file.writeBytes("data".toByteArray())
        try {
            val result = client.send(device, file)
            assertIs<SendResult.Failure>(result)
            assertFalse(uploadCalled.get(), "upload must not be called when pairing rejected")
        } finally {
            client.close()
            Files.deleteIfExists(file)
            fixtures.tearDown()
        }
    }

    @Test
    fun `send returns Failure when user declines pairing`() = runTest {
        val serverKey = byteArrayOf(7, 8, 9)
        val uploadCalled = AtomicBoolean(false)
        val client = pairingClient(
            pairHandler = {
                respond(
                    content = Json.encodeToString(PairResponse(serverKey)),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
            uploadHandler = {
                uploadCalled.set(true)
                respond(content = "", status = HttpStatusCode.OK)
            },
            confirmationHandler = PairingConfirmationHandler { _, _ -> false },
        )
        val file = Files.createTempFile("user-decline-test", ".txt")
        file.writeBytes("data".toByteArray())
        try {
            val result = client.send(device, file)
            assertIs<SendResult.Failure>(result)
            assertFalse(uploadCalled.get(), "upload must not be called when user declines")
        } finally {
            client.close()
            Files.deleteIfExists(file)
            fixtures.tearDown()
        }
    }

    @Test
    fun `send skips pair prompt for already-trusted device`() = runTest {
        val serverKey = byteArrayOf(7, 8, 9)
        val serverDeviceId = deviceIdFromPublicKey(serverKey)
        val store = fixtures.newTrustedStore()
        store.saveTrustedKey(serverDeviceId, serverKey)
        val confirmationCalled = AtomicBoolean(false)
        val client = pairingClient(
            pairHandler = {
                respond(
                    content = Json.encodeToString(PairResponse(serverKey)),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
            uploadHandler = {
                respond(
                    content = okResponse("/saved/file.txt"),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
            trustedDeviceStore = store,
            confirmationHandler = PairingConfirmationHandler { _, _ ->
                confirmationCalled.set(true)
                true
            },
        )
        val file = Files.createTempFile("already-trusted-test", ".txt")
        file.writeBytes("data".toByteArray())
        try {
            val result = client.send(device, file)
            assertIs<SendResult.Success>(result)
            assertFalse(confirmationCalled.get(), "confirmation must not be shown for already-trusted device")
        } finally {
            client.close()
            Files.deleteIfExists(file)
            fixtures.tearDown()
        }
    }

    @Test
    fun `second send skips pairing confirmation after successful first pairing`() = runTest {
        val store = fixtures.newTrustedStore()
        var confirmationCallCount = 0
        val client = pairingClient(
            pairHandler = { respondJson(PairResponse(byteArrayOf(7, 8, 9))) },
            uploadHandler = { respondJson(mapOf("savedPath" to "/test/file.txt")) },
            trustedDeviceStore = store,
            confirmationHandler = PairingConfirmationHandler { _, _ ->
                confirmationCallCount++
                true
            },
        )
        val file = Files.createTempFile("consecutive-send-test", ".txt")
        file.writeBytes("hello".toByteArray())
        try {
            client.send(device, file)
            assertEquals(1, confirmationCallCount, "first send must trigger confirmation")

            client.send(device, ByteReadChannel("hello".toByteArray()), "test.txt", 5L)
            assertEquals(1, confirmationCallCount, "second send must not trigger confirmation again")
        } finally {
            client.close()
            Files.deleteIfExists(file)
            fixtures.tearDown()
        }
    }
}

private fun MockRequestHandleScope.respondJson(value: PairResponse): HttpResponseData = respond(
    content = Json.encodeToString(value),
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)

private fun MockRequestHandleScope.respondJson(value: Map<String, String>): HttpResponseData = respond(
    content = Json.encodeToString(value),
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)
