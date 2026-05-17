package com.tubetoast.tether.network

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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileServerPathTest {
    private val cleanupPaths = mutableListOf<File>()
    private var startedServer: FileServer? = null

    @AfterTest
    fun teardown() {
        startedServer?.stop()
        startedServer = null
        cleanupPaths.forEach { it.deleteRecursively() }
        cleanupPaths.clear()
    }

    private fun newServer(downloadsDir: File): FileServer {
        val configDir = Files.createTempDirectory("tether-fs-keys").toFile().also(cleanupPaths::add)
        val server = FileServer(
            port = 0,
            downloadsDir = downloadsDir,
            trustedDeviceStore = TrustedDeviceStore(configDir),
            deviceKeyPair = DeviceKeyPair(configDir),
        )
        startedServer = server
        return server
    }

    private fun newDownloadsDir(): File =
        Files.createTempDirectory("tether-fs-dl").toFile().also(cleanupPaths::add)

    @Test
    fun `relative path preserved as subdirectory`() {
        val dir = newDownloadsDir()
        val server = newServer(dir)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val response = client.post("http://localhost:$port/upload?name=Vacation%2F2024%2FIMG.jpg") {
                    contentType(ContentType.Application.OctetStream)
                    setBody("photo".toByteArray())
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val savedPath = response.body<Map<String, String>>()["savedPath"]!!
                val saved = File(savedPath)
                assertTrue(saved.exists(), "File should exist at: $savedPath")
                assertTrue(
                    saved.canonicalPath.startsWith(dir.canonicalPath),
                    "File should be inside downloads dir",
                )
                assertTrue(
                    savedPath.contains("Vacation") && savedPath.contains("2024"),
                    "Subdirectory structure should be preserved in: $savedPath",
                )
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `traversal attempt is sanitised`() {
        val dir = newDownloadsDir()
        val server = newServer(dir)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val response = client.post("http://localhost:$port/upload?name=..%2Fetc%2Fpasswd") {
                    contentType(ContentType.Application.OctetStream)
                    setBody("malicious".toByteArray())
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val savedPath = response.body<Map<String, String>>()["savedPath"]!!
                val saved = File(savedPath)
                assertTrue(
                    saved.canonicalPath.startsWith(dir.canonicalPath),
                    "File must be inside downloads dir, got: ${saved.canonicalPath}",
                )
                assertFalse(File(dir.parentFile, "etc/passwd").exists(), "Must not escape to /etc/passwd")
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `absolute path is rejected into safe location`() {
        val dir = newDownloadsDir()
        val server = newServer(dir)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val response = client.post("http://localhost:$port/upload?name=%2Fetc%2Fpasswd") {
                    contentType(ContentType.Application.OctetStream)
                    setBody("payload".toByteArray())
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val savedPath = response.body<Map<String, String>>()["savedPath"]!!
                val saved = File(savedPath)
                assertTrue(
                    saved.canonicalPath.startsWith(dir.canonicalPath),
                    "Absolute path must be redirected inside downloads dir, got: ${saved.canonicalPath}",
                )
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `collision applies leaf-only suffix keeping subdirectory`() {
        val dir = newDownloadsDir()
        val server = newServer(dir)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val r1 = client.post("http://localhost:$port/upload?name=photos%2Fimg.jpg") {
                    contentType(ContentType.Application.OctetStream)
                    setBody("first".toByteArray())
                }
                val path1 = r1.body<Map<String, String>>()["savedPath"]!!

                val r2 = client.post("http://localhost:$port/upload?name=photos%2Fimg.jpg") {
                    contentType(ContentType.Application.OctetStream)
                    setBody("second".toByteArray())
                }
                val path2 = r2.body<Map<String, String>>()["savedPath"]!!

                assertTrue(path1.contains("photos"), "First file should be in photos/ subdir")
                assertTrue(path2.contains("photos"), "Second file should still be in photos/ subdir")
                assertTrue(path1.endsWith("img.jpg"), "First file should be img.jpg")
                assertTrue(path2.endsWith("img_1.jpg"), "Collision file should be img_1.jpg")
            }
        } finally {
            client.close()
        }
    }
}
