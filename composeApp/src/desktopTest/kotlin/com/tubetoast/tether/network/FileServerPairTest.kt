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
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileServerPairTest {
    private val cleanupPaths = mutableListOf<File>()
    private var startedServer: FileServer? = null
    private val client: HttpClient = HttpClient(CIO) { install(ContentNegotiation) { json() } }

    @AfterTest
    fun teardown() {
        client.close()
        startedServer?.stop()
        startedServer = null
        cleanupPaths.forEach { it.deleteRecursively() }
        cleanupPaths.clear()
    }

    private fun newConfigDir(): File =
        Files.createTempDirectory("tether-pair-test").toFile().also(cleanupPaths::add)

    private fun startServer(store: TrustedDeviceStore, keyPair: DeviceKeyPair): Pair<FileServer, Int> {
        val server = FileServer(port = 0, trustedDeviceStore = store, deviceKeyPair = keyPair)
        startedServer = server
        return server to server.start()
    }

    @Test
    fun `pair endpoint returns 200 with server public key`() {
        val configDir = newConfigDir()
        val keyPair = DeviceKeyPair(configDir)
        val (_, port) = startServer(TrustedDeviceStore(configDir), keyPair)
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
    }

    @Test
    fun `pair saves initiator public key under publicKey-derived deviceId`() {
        val configDir = newConfigDir()
        val (_, port) = startServer(TrustedDeviceStore(configDir), DeviceKeyPair(configDir))
        val peerKey = byteArrayOf(10, 20, 30)
        val expectedDeviceId = deviceIdFromPublicKey(peerKey)
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
    }

    @Test
    fun `name collision with different keys produces distinct trust entries`() {
        val configDir = newConfigDir()
        val store = TrustedDeviceStore(configDir)
        val (_, port) = startServer(store, DeviceKeyPair(configDir))
        val keyAlice = byteArrayOf(1, 1, 1)
        val keyMallory = byteArrayOf(2, 2, 2)
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
        val aliceStored = store.getPublicKey(deviceIdFromPublicKey(keyAlice))
        val malloryStored = store.getPublicKey(deviceIdFromPublicKey(keyMallory))
        assertNotNull(aliceStored)
        assertNotNull(malloryStored)
        assertTrue(aliceStored.contentEquals(keyAlice), "alice's key must be preserved")
        assertTrue(
            malloryStored.contentEquals(keyMallory),
            "mallory cannot overwrite alice's trust by reusing the deviceName",
        )
    }

    @Test
    fun `pair with empty publicKey is accepted and stored under SHA-256 of empty input`() {
        val configDir = newConfigDir()
        val store = TrustedDeviceStore(configDir)
        val (_, port) = startServer(store, DeviceKeyPair(configDir))
        val emptyKey = byteArrayOf()
        val deviceId = deviceIdFromPublicKey(emptyKey)
        runBlocking {
            val response = client.post("http://localhost:$port/pair") {
                contentType(ContentType.Application.Json)
                setBody(PairRequest(publicKey = emptyKey, deviceName = "EmptyKeyPeer"))
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
        val stored = store.getPublicKey(deviceId)
        assertNotNull(stored, "empty publicKey must still produce a deterministic deviceId entry")
        assertEquals(0, stored.size, "stored bytes must round-trip the empty array")
    }

    @Test
    fun `pair with invalid body returns 400`() {
        val configDir = newConfigDir()
        val (_, port) = startServer(TrustedDeviceStore(configDir), DeviceKeyPair(configDir))
        runBlocking {
            val response = client.post("http://localhost:$port/pair") {
                contentType(ContentType.Application.Json)
                setBody("not valid json at all")
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun `pair returns 500 when store fails to persist`() {
        // Regular file in place of configDir → store's writeText throws, same path as a real disk failure.
        val notADirectory = Files.createTempFile("tether-pair-fail", ".not-a-dir").toFile()
        cleanupPaths.add(notADirectory)
        val keyPairDir = newConfigDir()
        val (_, port) = startServer(TrustedDeviceStore(notADirectory), DeviceKeyPair(keyPairDir))
        runBlocking {
            val response = client.post("http://localhost:$port/pair") {
                contentType(ContentType.Application.Json)
                setBody(PairRequest(publicKey = byteArrayOf(7, 7, 7), deviceName = "Whatever"))
            }
            assertEquals(HttpStatusCode.InternalServerError, response.status)
        }
    }
}
