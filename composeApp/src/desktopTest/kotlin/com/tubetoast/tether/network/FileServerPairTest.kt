package com.tubetoast.tether.network

import com.tubetoast.tether.protocol.PairRequest
import com.tubetoast.tether.protocol.PairResponse
import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore
import com.tubetoast.tether.security.deviceIdFromPublicKey
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
    fun `pair saves initiator public key under publicKey-derived deviceId`() {
        val (server, _, configDir) = testPairServer()
        val peerKey = byteArrayOf(10, 20, 30)
        val expectedDeviceId = deviceIdFromPublicKey(peerKey)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                client.post("http://localhost:$port/pair") {
                    contentType(ContentType.Application.Json)
                    setBody(PairRequest(publicKey = peerKey, deviceName = "PeerDevice"))
                }
            }
            val reloaded = TrustedDeviceStore(configDir)
            assertTrue(
                reloaded.isTrusted(expectedDeviceId),
                "deviceId derived from publicKey must be trusted after pairing",
            )
            assertFalse(
                reloaded.isTrusted("PeerDevice"),
                "deviceName must NOT be used as trust-store key — it is unauthenticated and collidable",
            )
        } finally {
            client.close()
            server.stop()
            configDir.deleteRecursively()
        }
    }

    @Test
    fun `name collision with different keys produces distinct trust entries`() {
        val (server, _, configDir) = testPairServer()
        val keyAlice = byteArrayOf(1, 1, 1)
        val keyMallory = byteArrayOf(2, 2, 2)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                client.post("http://localhost:$port/pair") {
                    contentType(ContentType.Application.Json)
                    setBody(PairRequest(publicKey = keyAlice, deviceName = "Phone"))
                }
                client.post("http://localhost:$port/pair") {
                    contentType(ContentType.Application.Json)
                    setBody(PairRequest(publicKey = keyMallory, deviceName = "Phone"))
                }
            }
            val store = TrustedDeviceStore(configDir)
            val aliceStored = store.getPublicKey(deviceIdFromPublicKey(keyAlice))
            val malloryStored = store.getPublicKey(deviceIdFromPublicKey(keyMallory))
            assertNotNull(aliceStored)
            assertNotNull(malloryStored)
            assertTrue(aliceStored.contentEquals(keyAlice), "alice's key must be preserved")
            assertTrue(
                malloryStored.contentEquals(keyMallory),
                "mallory cannot overwrite alice's trust by reusing the deviceName",
            )
        } finally {
            client.close()
            server.stop()
            configDir.deleteRecursively()
        }
    }

    @Test
    fun `pair returns 500 when store fails to persist`() {
        // Point the store at an existing regular file: configDir.mkdirs() returns false and
        // writeText on the storage file throws because the parent is not a directory. Same
        // exception path as a real disk failure (read-only FS, full disk, EACCES).
        val keyPairDir = Files.createTempDirectory("tether-pair-fail-keys").toFile()
        val notADirectory = Files.createTempFile("tether-pair-fail", ".not-a-dir").toFile()
        val keyPair = DeviceKeyPair(keyPairDir)
        val throwingStore = TrustedDeviceStore(notADirectory)
        val server = FileServer(0, trustedDeviceStore = throwingStore, deviceKeyPair = keyPair)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val response = client.post("http://localhost:$port/pair") {
                    contentType(ContentType.Application.Json)
                    setBody(PairRequest(publicKey = byteArrayOf(7, 7, 7), deviceName = "Whatever"))
                }
                assertEquals(HttpStatusCode.InternalServerError, response.status)
            }
        } finally {
            client.close()
            server.stop()
            keyPairDir.deleteRecursively()
            notADirectory.delete()
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
