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
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
}
