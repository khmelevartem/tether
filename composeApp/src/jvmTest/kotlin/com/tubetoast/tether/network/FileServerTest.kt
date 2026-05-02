package com.tubetoast.tether.network

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
