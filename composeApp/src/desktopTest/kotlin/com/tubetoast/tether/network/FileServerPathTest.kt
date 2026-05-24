package com.tubetoast.tether.network

import com.tubetoast.tether.logging.suppressTestLogs
import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// real CIO server — CIOApplicationEngine hardcodes real-thread dispatchers
@Suppress("ktlint:tether:no-run-blocking-in-tests")
class FileServerPathTest {
    private val cleanupPaths = mutableListOf<File>()
    private var startedServer: FileServer? = null

    @BeforeTest
    fun setup() {
        suppressTestLogs()
    }

    @AfterTest
    fun teardown() {
        startedServer?.stop()
        startedServer = null
        cleanupPaths.forEach { it.deleteRecursively() }
        cleanupPaths.clear()
    }

    private fun newServer(downloadsDir: File): FileServer {
        val configDir = Files.createTempDirectory("tether-path-test-keys").toFile().also(cleanupPaths::add)
        val server = FileServer(
            port = 0,
            downloadsDir = downloadsDir,
            trustedDeviceStore = TrustedDeviceStore(configDir),
            deviceKeyPair = DeviceKeyPair(configDir),
        )
        startedServer = server
        return server
    }

    @Test
    fun `nested path lands at correct subdirectory`() {
        val root = Files.createTempDirectory("tether-path-nested").toFile().also(cleanupPaths::add)
        val server = newServer(root)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val response = client.post("http://localhost:$port/upload?name=foo/bar.txt") {
                    contentType(ContentType.Application.OctetStream)
                    setBody("nested content".toByteArray())
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val savedPath = response.body<Map<String, String>>()["savedPath"]!!
                val saved = File(savedPath)
                assertTrue(saved.exists())
                assertEquals("nested content", saved.readText())
                assertTrue(
                    saved.canonicalPath.startsWith(root.canonicalPath),
                    "saved file must be inside downloads root",
                )
                assertEquals("bar.txt", saved.name)
                assertEquals("foo", saved.parentFile.name)
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `dotdot traversal returns 400`() {
        val root = Files.createTempDirectory("tether-path-traversal").toFile().also(cleanupPaths::add)
        val server = newServer(root)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val response = client.post("http://localhost:$port/upload?name=../escape.txt") {
                    contentType(ContentType.Application.OctetStream)
                    setBody("malicious".toByteArray())
                }
                assertEquals(HttpStatusCode.BadRequest, response.status)
                val body = response.body<Map<String, String>>()
                assertEquals("invalid_relative_path", body["error"])
                assertFalse(File(root.parentFile, "escape.txt").exists(), "file must not be written outside root")
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `url-encoded traversal returns 400`() {
        val root = Files.createTempDirectory("tether-path-encoded-traversal").toFile().also(cleanupPaths::add)
        val server = newServer(root)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val response = client.post("http://localhost:$port/upload?name=%2e%2e%2fescape.txt") {
                    contentType(ContentType.Application.OctetStream)
                    setBody("malicious".toByteArray())
                }
                assertEquals(HttpStatusCode.BadRequest, response.status)
                val body = response.body<Map<String, String>>()
                assertEquals("invalid_relative_path", body["error"])
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `missing name returns 400 with invalid_relative_path`() {
        val root = Files.createTempDirectory("tether-path-missing-name").toFile().also(cleanupPaths::add)
        val server = newServer(root)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val response = client.post("http://localhost:$port/upload") {
                    contentType(ContentType.Application.OctetStream)
                    setBody(ByteArray(0))
                }
                assertEquals(HttpStatusCode.BadRequest, response.status)
                val body = response.body<Map<String, String>>()
                assertEquals("invalid_relative_path", body["error"])
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `flat filename still works as a single-segment relative path`() {
        val root = Files.createTempDirectory("tether-path-flat").toFile().also(cleanupPaths::add)
        val server = newServer(root)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val response = client.post("http://localhost:$port/upload?name=photo.jpg") {
                    contentType(ContentType.Application.OctetStream)
                    setBody("flat content".toByteArray())
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val savedPath = response.body<Map<String, String>>()["savedPath"]!!
                val saved = File(savedPath)
                assertTrue(saved.exists())
                assertEquals("flat content", saved.readText())
                assertEquals(root.canonicalPath, saved.parentFile.canonicalPath)
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `symlink pointing outside root triggers 500`() {
        val root = Files.createTempDirectory("tether-path-symlink-root").toFile().also(cleanupPaths::add)
        val outside = Files.createTempDirectory("tether-path-symlink-outside").toFile().also(cleanupPaths::add)
        val symlink = File(root, "link-dir")
        Files.createSymbolicLink(symlink.toPath(), outside.toPath())
        val server = newServer(root)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val response = client.post("http://localhost:$port/upload?name=link-dir/secret.txt") {
                    contentType(ContentType.Application.OctetStream)
                    setBody("should not land here".toByteArray())
                }
                assertEquals(HttpStatusCode.InternalServerError, response.status)
                assertFalse(File(outside, "secret.txt").exists(), "file must not escape via symlink")
            }
        } finally {
            client.close()
        }
    }
}
