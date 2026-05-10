@file:OptIn(ExperimentalForeignApi::class)

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
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.stringWithContentsOfFile
import platform.posix.memcpy
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

class FileServerTest {
    private val tempDirs = mutableListOf<String>()

    @AfterTest
    fun cleanup() {
        val fm = NSFileManager.defaultManager
        tempDirs.forEach { fm.removeItemAtPath(it, error = null) }
        tempDirs.clear()
    }

    private fun newTempDir(): String {
        val path = "${NSTemporaryDirectory()}tether-${NSUUID().UUIDString}"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        tempDirs += path
        return path
    }

    private fun makeClient(): HttpClient = HttpClient(CIO) { install(ContentNegotiation) { json() } }

    @Test
    fun health_endpoint_returns_200() {
        val server = FileServer(port = 0, downloadsDir = newTempDir())
        val port = server.start()
        val client = makeClient()
        try {
            runBlocking {
                val response = client.get("http://localhost:$port/health")
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("Tether OK", response.bodyAsText())
            }
        } finally {
            client.close()
            server.stop()
        }
    }

    @Test
    fun start_returns_valid_port() {
        val server = FileServer(port = 0, downloadsDir = newTempDir())
        val port = server.start()
        try {
            assertTrue(port in 1024..65535, "Expected ephemeral port, got $port")
        } finally {
            server.stop()
        }
    }

    @Test
    fun stop_on_unstarted_does_not_throw() {
        FileServer(port = 0, downloadsDir = newTempDir()).stop()
    }

    @Test
    fun double_start_throws() {
        val server = FileServer(port = 0, downloadsDir = newTempDir())
        server.start()
        try {
            assertFailsWith<IllegalStateException> { server.start() }
        } finally {
            server.stop()
        }
    }

    @Test
    fun upload_saves_file_with_correct_content() {
        val dir = newTempDir()
        val server = FileServer(port = 0, downloadsDir = dir)
        val port = server.start()
        val client = makeClient()
        try {
            runBlocking {
                val payload = "hello tether ios".encodeToByteArray()
                val response = client.post("http://localhost:$port/upload?name=hello.txt") {
                    contentType(ContentType.Application.OctetStream)
                    setBody(payload)
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val body: Map<String, String> = response.body()
                val savedPath = body["savedPath"]
                assertNotNull(savedPath)
                assertTrue(NSFileManager.defaultManager.fileExistsAtPath(savedPath))
                assertEquals("hello tether ios", readFileAsString(savedPath))
            }
        } finally {
            client.close()
            server.stop()
        }
    }

    @Test
    fun upload_without_name_returns_400() {
        val server = FileServer(port = 0, downloadsDir = newTempDir())
        val port = server.start()
        val client = makeClient()
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
        }
    }

    @Test
    fun upload_duplicate_filename_gets_numeric_suffix() {
        val dir = newTempDir()
        val server = FileServer(port = 0, downloadsDir = dir)
        val port = server.start()
        val client = makeClient()
        try {
            runBlocking {
                val r1 = client.post("http://localhost:$port/upload?name=dup.txt") {
                    contentType(ContentType.Application.OctetStream)
                    setBody("first".encodeToByteArray())
                }
                val path1 = (r1.body() as Map<String, String>)["savedPath"]!!

                val r2 = client.post("http://localhost:$port/upload?name=dup.txt") {
                    contentType(ContentType.Application.OctetStream)
                    setBody("second".encodeToByteArray())
                }
                val path2 = (r2.body() as Map<String, String>)["savedPath"]!!

                assertTrue(path1.endsWith("dup.txt"), "first should be dup.txt, got $path1")
                assertTrue(path2.endsWith("dup_1.txt"), "second should be dup_1.txt, got $path2")
                assertEquals("first", readFileAsString(path1))
                assertEquals("second", readFileAsString(path2))
            }
        } finally {
            client.close()
            server.stop()
        }
    }

    @Test
    fun upload_strips_path_traversal() {
        val dir = newTempDir()
        val server = FileServer(port = 0, downloadsDir = dir)
        val port = server.start()
        val client = makeClient()
        try {
            runBlocking {
                val response = client.post("http://localhost:$port/upload?name=../evil.txt") {
                    contentType(ContentType.Application.OctetStream)
                    setBody("malicious".encodeToByteArray())
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val savedPath = (response.body() as Map<String, String>)["savedPath"]!!
                assertTrue(
                    savedPath.startsWith("$dir/"),
                    "saved path must be inside downloads dir: $savedPath",
                )
                val parentEvil = "${dir.substringBeforeLast('/')}/evil.txt"
                assertFalse(
                    NSFileManager.defaultManager.fileExistsAtPath(parentEvil),
                    "file must not escape downloads dir",
                )
            }
        } finally {
            client.close()
            server.stop()
        }
    }

    @Test
    fun upload_to_unwritable_destination_returns_error_and_no_partial_file() {
        // downloadsDir is a regular file, not a directory. ensureDirectory is a no-op
        // (fileExistsAtPath is true), resolveDestination yields "<file>/upload.txt",
        // and fopen on a non-directory parent fails with ENOTDIR. This exercises the
        // catch/finally cleanup path that fwrite-failure shares — proving an I/O
        // error mid-upload yields a non-2xx response and no partial file.
        val parent = newTempDir()
        val regularFile = "$parent/not-a-dir.txt"
        NSFileManager.defaultManager.createFileAtPath(regularFile, contents = null, attributes = null)
        val server = FileServer(port = 0, downloadsDir = regularFile)
        val port = server.start()
        val client = makeClient()
        try {
            runBlocking {
                val response = client.post("http://localhost:$port/upload?name=upload.txt") {
                    contentType(ContentType.Application.OctetStream)
                    setBody("payload".encodeToByteArray())
                }
                assertEquals(HttpStatusCode.InternalServerError, response.status)
                val candidate = "$regularFile/upload.txt"
                assertFalse(
                    NSFileManager.defaultManager.fileExistsAtPath(candidate),
                    "no partial file should remain at $candidate",
                )
            }
        } finally {
            client.close()
            server.stop()
        }
    }

    @Test
    fun streamUploadBody_propagates_writer_exception() = runBlocking {
        // Guards the structural invariant that the I/O write path must throw on
        // failure rather than silently swallowing errors. If a future contributor
        // discards a syscall return value (the bug fixed in this PR was exactly
        // this), the writer lambda would still run all chunks and the upload route
        // would respond 200 OK on a truncated file. This test pins the contract.
        val input = ByteReadChannel("hello tether".encodeToByteArray())
        val ex = assertFailsWith<IllegalStateException> {
            streamUploadBody(input) { _, _ ->
                error("simulated I/O failure")
            }
        }
        assertTrue(
            ex.message?.contains("simulated I/O failure") == true,
            "expected simulated failure to propagate, got: ${ex.message}",
        )
    }

    @Test
    fun upload_creates_downloads_dir_if_missing() {
        val parent = newTempDir()
        val nested = "$parent/deep/nested"
        assertFalse(NSFileManager.defaultManager.fileExistsAtPath(nested))
        val server = FileServer(port = 0, downloadsDir = nested)
        val port = server.start()
        val client = makeClient()
        try {
            runBlocking {
                client.post("http://localhost:$port/upload?name=x.bin") {
                    contentType(ContentType.Application.OctetStream)
                    setBody(ByteArray(1))
                }
            }
            assertTrue(NSFileManager.defaultManager.fileExistsAtPath(nested))
        } finally {
            client.close()
            server.stop()
        }
    }

    @Test
    fun restart_after_stop_succeeds() {
        // Issue #81 "Как должно работать" item 5: повторный start() после stop() работает.
        val server = FileServer(port = 0, downloadsDir = newTempDir())
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
    fun upload_streams_5mb_body_byte_identical() {
        // Issue #81 "Нефункциональные требования": streaming, без буферизации
        // целиком в память. Native impl uses POSIX fopen/fwrite — diverges
        // most from JVM and most worth pinning down on a non-trivial payload.
        val dir = newTempDir()
        val server = FileServer(port = 0, downloadsDir = dir)
        val port = server.start()
        val client = makeClient()
        try {
            val sizeMb = 5
            val payload = ByteArray(sizeMb * 1024 * 1024) { (it % 251).toByte() }
            runBlocking {
                val response = client.post("http://localhost:$port/upload?name=big.bin") {
                    contentType(ContentType.Application.OctetStream)
                    setBody(payload)
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val savedPath = (response.body() as Map<String, String>)["savedPath"]!!
                val saved = readFileAsByteArray(savedPath)
                assertEquals(payload.size, saved.size, "size mismatch")
                assertContentEquals(payload, saved, "content mismatch on $sizeMb MB roundtrip")
            }
        } finally {
            client.close()
            server.stop()
        }
    }

    @Test
    fun client_disconnect_mid_upload_leaves_no_partial_file() {
        // Issue #81 "Краевые случаи": клиент закрыл коннекшен в середине
        // загрузки — частичный файл удаляется. The OutgoingContent below
        // declares a Content-Length (mirroring production FileClient sending
        // a known-size file) and streams the body slowly with a per-chunk
        // delay, so withTimeout reliably fires mid-transfer regardless of
        // platform speed. Server compares received bytes vs Content-Length.
        val dir = newTempDir()
        val server = FileServer(port = 0, downloadsDir = dir)
        val port = server.start()
        val client = makeClient()
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
                    // expected
                } catch (_: Exception) {
                    // any other I/O exception from the client is also acceptable —
                    // we only care that the upload did not complete with 200 OK.
                }

                // Allow server-side catch/finally to run.
                delay(200.milliseconds)

                val files = NSFileManager.defaultManager
                    .contentsOfDirectoryAtPath(dir, error = null) as? List<*> ?: emptyList<Any?>()
                val partial = files.filterIsInstance<String>().filter { it.startsWith("trunc") }
                assertTrue(
                    partial.isEmpty(),
                    "no partial file should remain in $dir, found: $partial",
                )
            }
        } finally {
            client.close()
            server.stop()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun readFileAsString(path: String): String =
    NSString.stringWithContentsOfFile(
        path = path,
        encoding = NSUTF8StringEncoding,
        error = null,
    ) as String

@OptIn(ExperimentalForeignApi::class)
private fun readFileAsByteArray(path: String): ByteArray {
    val data = NSData.dataWithContentsOfFile(path) ?: error("file not found: $path")
    val length = data.length.toInt()
    val out = ByteArray(length)
    if (length > 0) {
        out.usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, length.toULong())
        }
    }
    return out
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
