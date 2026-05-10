package com.tubetoast.tether.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

class FileServerTest {
    @Test
    fun `health endpoint returns 200 with Tether OK`() {
        val server = FileServer(0)
        val port = server.start()
        try {
            val client = HttpClient(CIO)
            runBlocking {
                val response = client.get("http://localhost:$port/health")
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("Tether OK", response.bodyAsText())
            }
            client.close()
        } finally {
            server.stop()
        }
    }

    @Test
    fun `start returns a valid port in usable range`() {
        val server = FileServer(0)
        val port = server.start()
        try {
            assertTrue(port in 1024..65535, "Expected ephemeral port, got $port")
        } finally {
            server.stop()
        }
    }

    @Test
    fun `stop on unstarted server does not throw`() {
        FileServer(0).stop()
    }

    @Test
    fun `double start throws IllegalStateException`() {
        val server = FileServer(0)
        server.start()
        try {
            var threw = false
            try {
                server.start()
            } catch (e: IllegalStateException) {
                threw = true
            }
            assertTrue(threw, "Expected IllegalStateException on double start")
        } finally {
            server.stop()
        }
    }

    @Test
    fun `upload saves file with correct content`() {
        val tmpDir = Files.createTempDirectory("tether-test").toFile()
        val server = FileServer(0, downloadsDir = tmpDir)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            val content = "hello tether upload".toByteArray()
            runBlocking {
                val response = client.post("http://localhost:$port/upload?name=hello.txt") {
                    contentType(ContentType.Application.OctetStream)
                    setBody(content)
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val body = response.body<Map<String, String>>()
                val savedPath = body["savedPath"]!!
                val saved = File(savedPath)
                assertTrue(saved.exists())
                assertEquals("hello tether upload", saved.readText())
            }
        } finally {
            client.close()
            server.stop()
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun `upload without name returns 400`() {
        val tmpDir = Files.createTempDirectory("tether-test").toFile()
        val server = FileServer(0, downloadsDir = tmpDir)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val response = client.post("http://localhost:$port/upload") {
                    contentType(ContentType.Application.OctetStream)
                    setBody(ByteArray(0))
                }
                assertEquals(HttpStatusCode.BadRequest, response.status)
            }
        } finally {
            client.close()
            server.stop()
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun `upload duplicate filename gets numeric suffix`() {
        val tmpDir = Files.createTempDirectory("tether-test").toFile()
        val server = FileServer(0, downloadsDir = tmpDir)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val r1 = client.post("http://localhost:$port/upload?name=dup.txt") {
                    contentType(ContentType.Application.OctetStream)
                    setBody("first".toByteArray())
                }
                val path1 = r1.body<Map<String, String>>()["savedPath"]!!

                val r2 = client.post("http://localhost:$port/upload?name=dup.txt") {
                    contentType(ContentType.Application.OctetStream)
                    setBody("second".toByteArray())
                }
                val path2 = r2.body<Map<String, String>>()["savedPath"]!!

                assertTrue(path1.endsWith("dup.txt"), "first should be dup.txt, got $path1")
                assertTrue(path2.endsWith("dup_1.txt"), "second should be dup_1.txt, got $path2")
                assertEquals("first", File(path1).readText())
                assertEquals("second", File(path2).readText())
            }
        } finally {
            client.close()
            server.stop()
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun `upload creates downloads dir if missing`() {
        val tmpDir = Files.createTempDirectory("tether-test").toFile()
        val nested = File(tmpDir, "deep/nested")
        assertFalse(nested.exists())
        val server = FileServer(0, downloadsDir = nested)
        val port = server.start()
        val client = HttpClient(CIO)
        try {
            runBlocking {
                client.post("http://localhost:$port/upload?name=x.bin") {
                    contentType(ContentType.Application.OctetStream)
                    setBody(ByteArray(1))
                }
            }
            assertTrue(nested.exists())
        } finally {
            client.close()
            server.stop()
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun `upload strips path traversal from filename`() {
        val tmpDir = Files.createTempDirectory("tether-test").toFile()
        val server = FileServer(0, downloadsDir = tmpDir)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val response = client.post("http://localhost:$port/upload?name=../evil.txt") {
                    contentType(ContentType.Application.OctetStream)
                    setBody("malicious".toByteArray())
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val savedPath = response.body<Map<String, String>>()["savedPath"]!!
                assertFalse(File(tmpDir.parentFile, "evil.txt").exists(), "file must not escape downloads dir")
                assertTrue(
                    File(savedPath).canonicalPath.startsWith(tmpDir.canonicalPath),
                    "saved path must be inside downloads dir",
                )
            }
        } finally {
            client.close()
            server.stop()
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun `restart after stop succeeds`() {
        val server = FileServer(0)
        val port1 = server.start()
        assertTrue(port1 in 1024..65535)
        server.stop()
        val port2 = server.start()
        try {
            assertTrue(port2 in 1024..65535)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `upload streams 5MB body byte identical`() {
        val tmpDir = Files.createTempDirectory("tether-test").toFile()
        val server = FileServer(0, downloadsDir = tmpDir)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            val sizeMb = 5
            val payload = ByteArray(sizeMb * 1024 * 1024) { (it % 251).toByte() }
            runBlocking {
                val response = client.post("http://localhost:$port/upload?name=big.bin") {
                    contentType(ContentType.Application.OctetStream)
                    setBody(payload)
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val savedPath = response.body<Map<String, String>>()["savedPath"]!!
                val saved = File(savedPath).readBytes()
                assertEquals(payload.size, saved.size, "size mismatch")
                assertContentEquals(payload, saved, "content mismatch on $sizeMb MB roundtrip")
            }
        } finally {
            client.close()
            server.stop()
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun `client disconnect mid upload leaves no partial file`() {
        // SlowContent declares Content-Length and paces with per-chunk delay so
        // withTimeout fires mid-transfer deterministically across platforms.
        val tmpDir = Files.createTempDirectory("tether-test").toFile()
        val server = FileServer(0, downloadsDir = tmpDir)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                try {
                    withTimeout(150.milliseconds) {
                        client.post("http://localhost:$port/upload?name=trunc.bin") {
                            setBody(SlowContent(totalBytes = 8L * 1024 * 1024))
                        }
                    }
                    fail("expected cancellation, request unexpectedly completed")
                } catch (_: TimeoutCancellationException) {
                } catch (_: Exception) {
                    // Any client-side I/O failure is acceptable; we only care that
                    // the upload did not complete with 200 OK.
                }

                delay(200.milliseconds) // let server-side catch/finally settle

                val partial = tmpDir.listFiles()?.filter { it.name.startsWith("trunc") } ?: emptyList()
                assertTrue(
                    partial.isEmpty(),
                    "no partial file should remain in $tmpDir, found: ${partial.map { it.name }}",
                )
            }
        } finally {
            client.close()
            server.stop()
            tmpDir.deleteRecursively()
        }
    }
}

private class SlowContent(
    private val totalBytes: Long,
) : OutgoingContent.WriteChannelContent() {
    override val contentLength: Long = totalBytes
    override val contentType: ContentType = ContentType.Application.OctetStream

    override suspend fun writeTo(channel: ByteWriteChannel) {
        val chunk = ByteArray(64 * 1024) { 0x41 }
        var sent = 0L
        while (sent < totalBytes) {
            val n = minOf(chunk.size.toLong(), totalBytes - sent).toInt()
            channel.writeFully(chunk, 0, n)
            sent += n
            delay(20.milliseconds)
        }
    }
}
