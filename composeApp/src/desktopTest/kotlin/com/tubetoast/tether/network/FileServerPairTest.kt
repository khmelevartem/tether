package com.tubetoast.tether.network

import com.tubetoast.tether.PairingTestFixtures
import com.tubetoast.tether.network.PairingConfirmationHandler
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// real CIO server — CIOApplicationEngine hardcodes real-thread dispatchers
@Suppress("ktlint:tether:no-run-blocking-in-tests")
class FileServerPairTest {
    private val fixtures = PairingTestFixtures()
    private var startedServer: FileServer? = null
    private val client: HttpClient = HttpClient(CIO) { install(ContentNegotiation) { json() } }

    @AfterTest
    fun teardown() {
        client.close()
        startedServer?.stop()
        startedServer = null
        fixtures.tearDown()
    }

    private fun startServer(
        store: TrustedDeviceStore,
        keyPair: DeviceKeyPair,
        handler: PairingConfirmationHandler? = PairingConfirmationHandler { _, _ -> true },
        pairingTimeoutMillis: Long = DEFAULT_PAIRING_TIMEOUT_MS,
    ): Pair<FileServer, Int> {
        val server =
            FileServer(
                configuredPort = 0,
                trustedDeviceStore = store,
                deviceKeyPair = keyPair,
                pairingConfirmationHandler = handler,
                pairingTimeoutMillis = pairingTimeoutMillis,
            )
        startedServer = server
        return server to server.start()
    }

    @Test
    fun `pair endpoint returns 200 with server public key`() {
        val keyPair = DeviceKeyPair(fixtures.newConfigDir())
        val (_, port) = startServer(fixtures.newTrustedStore(), keyPair)
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
        val store = fixtures.newTrustedStore()
        val (_, port) = startServer(store, DeviceKeyPair(fixtures.newConfigDir()))
        val peerKey = byteArrayOf(10, 20, 30)
        val expectedDeviceId = deviceIdFromPublicKey(peerKey)
        runBlocking {
            client.post("http://localhost:$port/pair") {
                contentType(ContentType.Application.Json)
                setBody(PairRequest(publicKey = peerKey, deviceName = "PeerDevice"))
            }
            assertTrue(
                store.isTrusted(expectedDeviceId),
                "deviceId derived from publicKey must be trusted after pairing",
            )
            assertFalse(
                store.isTrusted("PeerDevice"),
                "deviceName must NOT be used as trust-store key — it is unauthenticated and collidable",
            )
        }
    }

    @Test
    fun `name collision with different keys produces distinct trust entries`() {
        val store = fixtures.newTrustedStore()
        val (_, port) = startServer(store, DeviceKeyPair(fixtures.newConfigDir()))
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
    }

    @Test
    fun `pair with empty publicKey is accepted and stored under SHA-256 of empty input`() {
        val store = fixtures.newTrustedStore()
        val (_, port) = startServer(store, DeviceKeyPair(fixtures.newConfigDir()))
        val emptyKey = byteArrayOf()
        val deviceId = deviceIdFromPublicKey(emptyKey)
        runBlocking {
            val response = client.post("http://localhost:$port/pair") {
                contentType(ContentType.Application.Json)
                setBody(PairRequest(publicKey = emptyKey, deviceName = "EmptyKeyPeer"))
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val stored = store.getPublicKey(deviceId)
            assertNotNull(stored, "empty publicKey must still produce a deterministic deviceId entry")
            assertEquals(0, stored.size, "stored bytes must round-trip the empty array")
        }
    }

    @Test
    fun `pair with invalid body returns 400`() {
        val (_, port) = startServer(fixtures.newTrustedStore(), DeviceKeyPair(fixtures.newConfigDir()))
        runBlocking {
            val response = client.post("http://localhost:$port/pair") {
                contentType(ContentType.Application.Json)
                setBody("not valid json at all")
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun `pair returns 403 when handler rejects`() {
        val (_, port) = startServer(
            fixtures.newTrustedStore(),
            DeviceKeyPair(fixtures.newConfigDir()),
            handler = PairingConfirmationHandler { _, _ -> false },
        )
        runBlocking {
            val response = client.post("http://localhost:$port/pair") {
                contentType(ContentType.Application.Json)
                setBody(PairRequest(publicKey = byteArrayOf(1, 2, 3), deviceName = "RejectMe"))
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }

    @Test
    fun `pair returns 200 when device is already trusted`() {
        val store = fixtures.newTrustedStore()
        val peerKey = byteArrayOf(5, 6, 7)
        val deviceId = deviceIdFromPublicKey(peerKey)
        runBlocking { store.saveTrustedKey(deviceId, peerKey) }
        val (_, port) = startServer(
            store,
            DeviceKeyPair(fixtures.newConfigDir()),
            handler = PairingConfirmationHandler { _, _ -> false },
        )
        runBlocking {
            val response = client.post("http://localhost:$port/pair") {
                contentType(ContentType.Application.Json)
                setBody(PairRequest(publicKey = peerKey, deviceName = "AlreadyPaired"))
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun `pair returns 403 on timeout`() {
        val (_, port) = startServer(
            fixtures.newTrustedStore(),
            DeviceKeyPair(fixtures.newConfigDir()),
            handler = PairingConfirmationHandler { _, _ ->
                delay(Long.MAX_VALUE)
                false
            },
            pairingTimeoutMillis = 200L,
        )
        runBlocking {
            // real CIO server with real-time delay — withTimeout guards against a broken timeout implementation
            val response = withTimeout(2_000L) {
                client.post("http://localhost:$port/pair") {
                    contentType(ContentType.Application.Json)
                    setBody(PairRequest(publicKey = byteArrayOf(42), deviceName = "Slow"))
                }
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }

    @Test
    fun `pair returns 500 when store fails to persist`() {
        val throwingStore = object : TrustedDeviceStore {
            override suspend fun isTrusted(deviceId: String) = false

            override suspend fun saveTrustedKey(deviceId: String, publicKey: ByteArray) =
                throw IllegalStateException("simulated DataStore write failure")

            override suspend fun getPublicKey(deviceId: String): ByteArray? = null
        }
        val (_, port) = startServer(throwingStore, DeviceKeyPair(fixtures.newConfigDir()))
        runBlocking {
            val response = client.post("http://localhost:$port/pair") {
                contentType(ContentType.Application.Json)
                setBody(PairRequest(publicKey = byteArrayOf(7, 7, 7), deviceName = "Whatever"))
            }
            assertEquals(HttpStatusCode.InternalServerError, response.status)
        }
    }
}
