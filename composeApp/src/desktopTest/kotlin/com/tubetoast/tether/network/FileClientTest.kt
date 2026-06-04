package com.tubetoast.tether.network

import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.SendResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class FileClientTest {
    private val device = Device(name = "test", host = "127.0.0.1", port = 8080)

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

    private fun TestScope.channelOf(payload: ByteArray): ByteChannel {
        val channel = ByteChannel(autoFlush = true)
        launch {
            channel.writeFully(payload)
            channel.close()
        }
        return channel
    }

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
        val payload = "hello from client".toByteArray()
        val source = channelOf(payload)
        try {
            val result = client.send(
                device,
                channel = source,
                fileName = "path.txt",
                totalBytes = payload.size.toLong(),
            )
            assertIs<SendResult.Success>(result)
            assertTrue(result.savedPath.endsWith(".txt"))
        } finally {
            client.close()
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
        val source = channelOf(payload)
        try {
            client.send(device, channel = source, fileName = "file.bin", totalBytes = payload.size.toLong())
            assertTrue(
                payload.contentEquals(captured.get()),
                "Captured content does not match sent content",
            )
        } finally {
            client.close()
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
        val source = channelOf(payload)
        try {
            client.send(
                device,
                channel = source,
                fileName = "content-length-test.bin",
                totalBytes = payload.size.toLong(),
            )
            val c = captured.get() ?: error("handler did not capture request")
            assertTrue(
                c.contentLength == payload.size.toLong(),
                "Content-Length must equal file size",
            )
            assertNull(c.transferEncoding, "Transfer-Encoding must be absent (no chunked)")
        } finally {
            client.close()
        }
    }

    @Test
    fun `send returns Failure when server is not running`() = runTest {
        val client = mockClient { throw IOException("connection refused") }
        val payload = "data".toByteArray()
        val source = channelOf(payload)
        try {
            val result = client.send(
                device,
                channel = source,
                fileName = "no-server.txt",
                totalBytes = payload.size.toLong(),
            )
            assertIs<SendResult.Failure>(result)
        } finally {
            client.close()
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
        val source = channelOf(payload)
        try {
            val result = client.send(
                device,
                channel = source,
                fileName = "progress-test.bin",
                totalBytes = payload.size.toLong(),
            ) { sent, _ -> observed.add(sent) }
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
        }
    }

    @Test
    fun `send encodes spaces in filename as percent-20 not plus`() = runTest {
        val capturedQuery = AtomicReference<String?>()
        val client = mockClient { request ->
            capturedQuery.set(request.url.encodedQuery)
            val body = request.body as OutgoingContent.ReadChannelContent
            val ch = body.readFrom()
            val buf = ByteArray(8 * 1024)
            while (!ch.isClosedForRead) ch.readAvailable(buf)
            respond(
                content = okResponse("/saved/manual with space.txt"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val source = ByteChannel(autoFlush = true)
        source.writeFully(ByteArray(4))
        source.close()
        try {
            client.send(
                device = device,
                channel = source,
                fileName = "manual with space.txt",
                totalBytes = 4L,
            )
            val query = capturedQuery.get() ?: error("handler did not capture request")
            assertTrue(
                query.contains("name=manual%20with%20space") && !query.contains('+'),
                "expected spaces encoded as %20 (not +) in query, got: $query",
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun `send encodes hash in filename as percent-23`() = runTest {
        val capturedQuery = AtomicReference<String?>()
        val client = mockClient { request ->
            capturedQuery.set(request.url.encodedQuery)
            val body = request.body as OutgoingContent.ReadChannelContent
            val ch = body.readFrom()
            val buf = ByteArray(8 * 1024)
            while (!ch.isClosedForRead) ch.readAvailable(buf)
            respond(
                content = okResponse("/saved/file#name.txt"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val source = ByteChannel(autoFlush = true)
        source.writeFully(ByteArray(4))
        source.close()
        try {
            client.send(
                device = device,
                channel = source,
                fileName = "file#name.txt",
                totalBytes = 4L,
            )
            val query = capturedQuery.get() ?: error("handler did not capture request")
            assertTrue(
                query.contains("name=file%23name"),
                "expected # encoded as %23 in query, got: $query",
            )
        } finally {
            client.close()
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
}
