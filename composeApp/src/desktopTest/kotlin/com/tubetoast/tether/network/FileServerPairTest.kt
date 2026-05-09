package com.tubetoast.tether.network

import com.tubetoast.tether.protocol.PairRequest
import com.tubetoast.tether.protocol.PairResponse
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileServerPairTest {
    private fun testPairServer(): Triple<FileServer, DeviceKeyPair, File> {
        val configDir = Files.createTempDirectory("tether-pair-test").toFile()
        val store = TrustedDeviceStore(configDir)
        val keyPair = DeviceKeyPair(configDir)
        val server = FileServer(0, trustedDeviceStore = store, deviceKeyPair = keyPair)
        return Triple(server, keyPair, configDir)
    }

    @Test
    fun `pair endpoint returns 200 with server public key`() {
        val (server, keyPair, configDir) = testPairServer()
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val response = client.post("http://localhost:$port/pair") {
                    contentType(ContentType.Application.Json)
                    setBody(PairRequest(publicKey = byteArrayOf(1, 2, 3, 4, 5), deviceName = "TestClient"))
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val body = response.body<PairResponse>()
                assertTrue(body.publicKey.isNotEmpty(), "server public key must be non-empty")
                assertTrue(
                    body.publicKey.contentEquals(keyPair.publicKey),
                    "server public key must match the server's key pair",
                )
            }
        } finally {
            client.close()
            server.stop()
            configDir.deleteRecursively()
        }
    }

    @Test
    fun `pair saves initiator public key to store`() {
        val (server, _, configDir) = testPairServer()
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                client.post("http://localhost:$port/pair") {
                    contentType(ContentType.Application.Json)
                    setBody(PairRequest(publicKey = byteArrayOf(10, 20, 30), deviceName = "PeerDevice"))
                }
            }
            assertTrue(
                TrustedDeviceStore(configDir).isTrusted("PeerDevice"),
                "PeerDevice must be trusted after pairing",
            )
        } finally {
            client.close()
            server.stop()
            configDir.deleteRecursively()
        }
    }

    @Test
    fun `pair with invalid body returns 400`() {
        val (server, _, configDir) = testPairServer()
        val port = server.start()
        val client = HttpClient(CIO)
        try {
            runBlocking {
                val response = client.post("http://localhost:$port/pair") {
                    contentType(ContentType.Application.Json)
                    setBody("not valid json at all")
                }
                assertEquals(HttpStatusCode.BadRequest, response.status)
            }
        } finally {
            client.close()
            server.stop()
            configDir.deleteRecursively()
        }
    }
}
