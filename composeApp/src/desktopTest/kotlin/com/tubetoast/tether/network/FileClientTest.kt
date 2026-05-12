package com.tubetoast.tether.network

import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.SendResult
import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class FileClientTest {
    private lateinit var tmpDir: File
    private lateinit var configDir: File
    private lateinit var server: FileServer
    private lateinit var client: FileClient
    private var serverPort: Int = 0

    private val device
        get() = Device(id = "test@127.0.0.1:$serverPort", name = "test", host = "127.0.0.1", port = serverPort)

    @BeforeTest
    fun setup() {
        tmpDir = Files.createTempDirectory("tether-client-test").toFile()
        configDir = Files.createTempDirectory("tether-client-test-keys").toFile()
        server = FileServer(
            port = 0,
            downloadsDir = tmpDir,
            trustedDeviceStore = TrustedDeviceStore(configDir),
            deviceKeyPair = DeviceKeyPair(configDir),
        )
        serverPort = server.start()
        client = FileClient()
    }

    @AfterTest
    fun teardown() {
        client.close()
        server.stop()
        tmpDir.deleteRecursively()
        configDir.deleteRecursively()
    }

    @Test
    fun `send returns Success with saved path`() {
        val file = Files.createTempFile("send-test", ".txt")
        file.writeBytes("hello from client".toByteArray())
        runBlocking {
            val result = client.send(device, file)
            assertIs<SendResult.Success>(result)
            assertTrue(result.savedPath.endsWith(".txt"))
            assertEquals("hello from client", File(result.savedPath).readText())
        }
        Files.deleteIfExists(file)
    }

    @Test
    fun `send throws FileNotFoundException for missing file`() {
        val missing = Files.createTempDirectory("gone").resolve("no-such-file.bin")
        runBlocking {
            assertFailsWith<FileNotFoundException> {
                client.send(device, missing)
            }
        }
    }

    @Test
    fun `send preserves file content exactly`() {
        val content = ByteArray(256) { it.toByte() }
        val file = Files.createTempFile("binary-test", ".bin")
        file.writeBytes(content)
        runBlocking {
            val result = client.send(device, file) as SendResult.Success
            val saved = File(result.savedPath).readBytes()
            assertTrue(content.contentEquals(saved), "Saved content does not match sent content")
        }
        Files.deleteIfExists(file)
    }

    @Test
    fun `send sets Content-Length and omits chunked transfer encoding when totalBytes known`() {
        data class Captured(
            val contentLength: Long?,
            val transferEncoding: String?,
        )
        val captured = AtomicReference<Captured>()
        val captureServer = embeddedServer(CIO, port = 0) {
            install(ContentNegotiation) { json() }
            routing {
                post("/upload") {
                    val channel = call.receiveChannel()
                    val buf = ByteArray(8 * 1024)
                    while (!channel.isClosedForRead) channel.readAvailable(buf)
                    captured.set(
                        Captured(
                            contentLength = call.request.contentLength(),
                            transferEncoding = call.request.headers[HttpHeaders.TransferEncoding],
                        ),
                    )
                    call.respond(mapOf("savedPath" to "ignored"))
                }
            }
        }.start(wait = false)
        val port = runBlocking {
            captureServer.engine
                .resolvedConnectors()
                .first()
                .port
        }
        val captureClient = FileClient()
        try {
            val payload = ByteArray(2048) { (it % 251).toByte() }
            val file = Files.createTempFile("content-length-test", ".bin")
            file.writeBytes(payload)
            runBlocking {
                captureClient.send(
                    device = Device(id = "x@127.0.0.1:$port", name = "x", host = "127.0.0.1", port = port),
                    file = file,
                )
            }
            Files.deleteIfExists(file)
            val c = captured.get() ?: error("server did not capture request")
            assertEquals(payload.size.toLong(), c.contentLength, "Content-Length must equal file size")
            assertNull(c.transferEncoding, "Transfer-Encoding must be absent (no chunked)")
        } finally {
            captureClient.close()
            captureServer.stop(0, 0)
        }
    }

    @Test
    fun `truncated upload with known totalBytes fails and leaves no partial file`() {
        val declared = 64L * 1024
        val delivered = 4 * 1024
        val partialChannel = ByteChannel(autoFlush = true)
        runBlocking {
            CoroutineScope(Dispatchers.IO).launch {
                partialChannel.writeFully(ByteArray(delivered) { 0x42 })
                partialChannel.cancel(java.io.IOException("simulated mid-upload disconnect"))
            }
            val result = client.send(
                device = device,
                channel = partialChannel,
                fileName = "trunc-prod.bin",
                totalBytes = declared,
            )
            assertIs<SendResult.Failure>(result)
            val deadline = TimeSource.Monotonic.markNow() + 3.seconds
            while (deadline.hasNotPassedNow()) {
                val partial = tmpDir.listFiles()?.any { it.name.startsWith("trunc-prod") } ?: false
                if (!partial) break
                delay(20.milliseconds)
            }
            val partial = tmpDir.listFiles()?.filter { it.name.startsWith("trunc-prod") } ?: emptyList()
            assertTrue(
                partial.isEmpty(),
                "no partial file should remain in $tmpDir, found: ${partial.map { it.name }}",
            )
        }
    }

    @Test
    fun `send returns Failure when server is not running`() {
        val file = Files.createTempFile("no-server", ".txt")
        file.writeBytes("data".toByteArray())
        val unreachable = Device(id = "x@127.0.0.1:1", name = "x", host = "127.0.0.1", port = 1)
        runBlocking {
            val result = client.send(unreachable, file)
            assertIs<SendResult.Failure>(result)
        }
        Files.deleteIfExists(file)
    }
}
