@file:OptIn(ExperimentalForeignApi::class)

package com.tubetoast.tether.network

import com.tubetoast.tether.preferences.TempDataStore
import com.tubetoast.tether.protocol.PairRequest
import com.tubetoast.tether.protocol.PairResponse
import com.tubetoast.tether.security.DefaultTrustedDeviceStore
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
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// real CIO server — CIOApplicationEngine hardcodes real-thread dispatchers
@Suppress("ktlint:tether:no-run-blocking-in-tests")
class FileServerPairTest {
    private val tempPaths = mutableListOf<String>()
    private val cleanupTempStores = mutableListOf<TempDataStore>()
    private lateinit var configDir: String
    private lateinit var store: DefaultTrustedDeviceStore
    private lateinit var keyPair: DeviceKeyPair
    private lateinit var server: FileServer
    private lateinit var client: HttpClient
    private var port: Int = 0

    @BeforeTest
    fun setup() {
        configDir = newTempDir()
        val temp = TempDataStore().also { cleanupTempStores += it }
        store = DefaultTrustedDeviceStore(temp.dataStore)
        keyPair = DeviceKeyPair(configDir)
        server = FileServer(
            port = 0,
            downloadsDir = newTempDir(),
            trustedDeviceStore = store,
            deviceKeyPair = keyPair,
        )
        port = server.start()
        client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
    }

    @AfterTest
    fun teardown() {
        client.close()
        server.stop()
        val fm = NSFileManager.defaultManager
        tempPaths.forEach { fm.removeItemAtPath(it, error = null) }
        tempPaths.clear()
        cleanupTempStores.forEach { it.tearDown() }
        cleanupTempStores.clear()
    }

    private fun newTempDir(): String {
        val path = "${NSTemporaryDirectory()}tether-apple-pair-${NSUUID().UUIDString}"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        tempPaths += path
        return path
    }

    @Test
    fun pair_endpoint_returns_200_with_server_public_key() {
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
    fun pair_returns_500_when_store_fails_to_persist() {
        val throwingStore = object : TrustedDeviceStore {
            override suspend fun isTrusted(deviceId: String) = false

            override suspend fun saveTrustedKey(deviceId: String, publicKey: ByteArray) = throw IllegalStateException(
                "simulated DataStore write failure",
            )

            override suspend fun getPublicKey(deviceId: String): ByteArray? = null
        }
        val failServer = FileServer(
            port = 0,
            downloadsDir = newTempDir(),
            trustedDeviceStore = throwingStore,
            deviceKeyPair = keyPair,
        )
        val failPort = failServer.start()
        try {
            runBlocking {
                val response = client.post("http://localhost:$failPort/pair") {
                    contentType(ContentType.Application.Json)
                    setBody(PairRequest(publicKey = byteArrayOf(9, 9, 9), deviceName = "FailPeer"))
                }
                assertEquals(HttpStatusCode.InternalServerError, response.status)
            }
        } finally {
            failServer.stop()
        }
    }

    @Test
    fun pair_saves_initiator_public_key_under_deviceId_derived_from_public_key() {
        val peerKey = byteArrayOf(10, 20, 30, 40, 50)
        val expectedDeviceId = deviceIdFromPublicKey(peerKey)
        runBlocking {
            client.post("http://localhost:$port/pair") {
                contentType(ContentType.Application.Json)
                setBody(PairRequest(publicKey = peerKey, deviceName = "AppleTestPeer"))
            }
            val storedKey = store.getPublicKey(expectedDeviceId)
            assertNotNull(storedKey, "deviceId derived from publicKey must be trusted after pairing")
            assertTrue(storedKey.contentEquals(peerKey), "stored key must round-trip the initiator's bytes")
        }
    }
}
