package com.tubetoast.tether.network

import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.preferences.TempDataStore
import com.tubetoast.tether.protocol.DeviceType
import com.tubetoast.tether.protocol.InfoDto
import com.tubetoast.tether.security.DefaultTrustedDeviceStore
import com.tubetoast.tether.security.DeviceKeyPair
import io.ktor.client.HttpClient
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

// real CIO server — CIOApplicationEngine hardcodes real-thread dispatchers
@Suppress("ktlint:tether:no-run-blocking-in-tests")
class HelloRouteTest {
    private val cleanupPaths = mutableListOf<File>()
    private val cleanupTempStores = mutableListOf<TempDataStore>()
    private var startedServer: FileServer? = null

    @AfterTest
    fun teardown() {
        startedServer?.stop()
        startedServer = null
        cleanupPaths.forEach { it.deleteRecursively() }
        cleanupPaths.clear()
        cleanupTempStores.forEach { it.tearDown() }
        cleanupTempStores.clear()
    }

    private fun newServer(
        ownFingerprint: String = "test-fp",
        store: DiscoveredDevicesStore = DiscoveredDevicesStore(),
    ): FileServer {
        val configDir = Files.createTempDirectory("tether-hello-test-keys").toFile().also(cleanupPaths::add)
        val downloadsDir = Files.createTempDirectory("tether-hello-test-dl").toFile().also(cleanupPaths::add)
        val temp = TempDataStore().also { cleanupTempStores += it }
        val server = FileServer(
            configuredPort = 0,
            downloadsDir = downloadsDir,
            trustedDeviceStore = DefaultTrustedDeviceStore(temp.dataStore),
            deviceKeyPair = DeviceKeyPair(configDir),
            ownFingerprint = { ownFingerprint },
            discoveredDevicesStore = store,
        )
        startedServer = server
        return server
    }

    @Test
    fun `hello from foreign fingerprint upserts device into store`() {
        val store = DiscoveredDevicesStore()
        val server = newServer(ownFingerprint = "mine", store = store)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val response = client.post("http://localhost:$port/hello") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        InfoDto(alias = "PeerA", fingerprint = "other-fp", port = 5000, deviceType = DeviceType.Mobile),
                    )
                }
                assertEquals(HttpStatusCode.OK, response.status)
            }
            assertTrue(store.devices.value.isNotEmpty(), "device must be upserted into store")
            assertEquals(
                "PeerA",
                store.devices.value
                    .first()
                    .name,
            )
            assertEquals(
                5000,
                store.devices.value
                    .first()
                    .port,
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun `hello with own fingerprint does not upsert into store`() {
        val store = DiscoveredDevicesStore()
        val server = newServer(ownFingerprint = "mine", store = store)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val response = client.post("http://localhost:$port/hello") {
                    contentType(ContentType.Application.Json)
                    setBody(InfoDto(alias = "Self", fingerprint = "mine", port = port, deviceType = DeviceType.Desktop))
                }
                assertEquals(HttpStatusCode.OK, response.status)
            }
            assertTrue(store.devices.value.isEmpty(), "self-hello must not upsert into store")
        } finally {
            client.close()
        }
    }

    @Test
    fun `malformed hello body returns 400 and route stays alive`() {
        val store = DiscoveredDevicesStore()
        val server = newServer(ownFingerprint = "mine", store = store)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                // Non-JSON body.
                val badResponse = client.post("http://localhost:$port/hello") {
                    contentType(ContentType.Application.Json)
                    setBody("not json at all")
                }
                assertTrue(
                    badResponse.status.value in 400..499,
                    "malformed body must yield 4xx, got ${badResponse.status}",
                )

                // Route must still accept a valid request afterwards.
                val goodResponse = client.post("http://localhost:$port/hello") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        InfoDto(
                            alias = "PeerOk",
                            fingerprint = "other-fp",
                            port = 5000,
                            deviceType = DeviceType.Desktop,
                        ),
                    )
                }
                assertEquals(HttpStatusCode.OK, goodResponse.status, "route must accept valid request after bad one")
            }
            assertTrue(store.devices.value.isNotEmpty(), "valid request must upsert device")
        } finally {
            client.close()
        }
    }

    @Test
    fun `hello with out-of-range port returns 400`() {
        val store = DiscoveredDevicesStore()
        val server = newServer(ownFingerprint = "mine", store = store)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val response = client.post("http://localhost:$port/hello") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        InfoDto(alias = "BadPort", fingerprint = "other-fp", port = 0, deviceType = DeviceType.Desktop),
                    )
                }
                assertTrue(
                    response.status.value in 400..499,
                    "port=0 must yield 4xx, got ${response.status}",
                )
            }
            assertTrue(store.devices.value.isEmpty(), "invalid port must not upsert device")
        } finally {
            client.close()
        }
    }

    @Test
    fun `hello uses TCP remoteAddress not body ip for device host`() {
        val store = DiscoveredDevicesStore()
        val server = newServer(ownFingerprint = "mine", store = store)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                client.post("http://localhost:$port/hello") {
                    contentType(ContentType.Application.Json)
                    setBody(InfoDto(alias = "B", fingerprint = "other", port = 6000, deviceType = DeviceType.Desktop))
                }
            }
            val device = store.devices.value.firstOrNull()
            assertFalse(device == null, "device should be upserted")
            // The host is taken from the TCP connection remote address (loopback), not from the body.
            assertFalse(device.host.contains("6000"), "host must not embed port from body")
        } finally {
            client.close()
        }
    }
}
