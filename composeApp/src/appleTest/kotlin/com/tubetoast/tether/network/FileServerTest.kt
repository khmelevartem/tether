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
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.stringWithContentsOfFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
}

@OptIn(ExperimentalForeignApi::class)
private fun readFileAsString(path: String): String =
    NSString.stringWithContentsOfFile(
        path = path,
        encoding = NSUTF8StringEncoding,
        error = null,
    ) as String
