package com.tubetoast.tether.network

import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.identity.DataStoreFingerprintPersistence
import com.tubetoast.tether.identity.DeviceIdentityStore
import com.tubetoast.tether.preferences.TempDataStore
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.DeviceType
import com.tubetoast.tether.protocol.PeerAnnouncement
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
        identityStore: DeviceIdentityStore =
            DeviceIdentityStore(
                DataStoreFingerprintPersistence(TempDataStore().also { cleanupTempStores += it }.dataStore),
            ),
        store: DiscoveredDevicesStore = DiscoveredDevicesStore(),
    ): FileServer {
        val configDir = Files.createTempDirectory("tether-hello-test-keys").toFile().also(cleanupPaths::add)
        val downloadsDir = Files.createTempDirectory("tether-hello-test-dl").toFile().also(cleanupPaths::add)
        val keyTemp = TempDataStore().also { cleanupTempStores += it }
        val server = FileServer(
            configuredPort = 0,
            downloadsDir = downloadsDir,
            trustedDeviceStore = DefaultTrustedDeviceStore(keyTemp.dataStore),
            deviceKeyPair = DeviceKeyPair(configDir),
            deviceIdentityStore = identityStore,
            discoveredDevicesStore = store,
        )
        startedServer = server
        return server
    }

    @Test
    fun `hello from foreign fingerprint upserts device into store`() {
        val store = DiscoveredDevicesStore()
        val server = newServer(store = store)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val response = client.post("http://localhost:$port/hello") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        PeerAnnouncement(
                            alias = "PeerA",
                            fingerprint = "other-fp",
                            port = 5000,
                            deviceType = DeviceType.Android,
                        ),
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
        val identityTemp = TempDataStore().also { cleanupTempStores += it }
        val identityStore = DeviceIdentityStore(DataStoreFingerprintPersistence(identityTemp.dataStore))
        val server = newServer(identityStore = identityStore, store = store)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val ownFingerprint = identityStore.getOrCreate()
                val response = client.post("http://localhost:$port/hello") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        PeerAnnouncement(
                            alias = "Self",
                            fingerprint = ownFingerprint,
                            port = port,
                            deviceType = DeviceType.Desktop,
                        ),
                    )
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
        val server = newServer(store = store)
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
                        PeerAnnouncement(
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
        val server = newServer(store = store)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val response = client.post("http://localhost:$port/hello") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        PeerAnnouncement(
                            alias = "BadPort",
                            fingerprint = "other-fp",
                            port = 0,
                            deviceType = DeviceType.Desktop,
                        ),
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
    fun `hello with already-known fingerprint does not overwrite canonical name`() {
        val store = DiscoveredDevicesStore()
        val server = newServer(store = store)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                // Simulate mDNS having already inserted the peer with its canonical name.
                store.upsert(
                    Device(
                        name = "Host (2)",
                        host = "192.168.1.5",
                        port = 5000,
                        fingerprint = "fp-canonical",
                    ),
                )

                // /hello arrives carrying the raw configured name for the same fingerprint.
                val response = client.post("http://localhost:$port/hello") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        PeerAnnouncement(
                            alias = "Host",
                            fingerprint = "fp-canonical",
                            port = 5000,
                            deviceType = DeviceType.Desktop,
                        ),
                    )
                }
                assertEquals(HttpStatusCode.OK, response.status)
            }
            assertEquals(1, store.devices.value.size, "no duplicate should be added")
            assertEquals(
                "Host (2)",
                store.devices.value
                    .first()
                    .name,
                "canonical name must not be overwritten by /hello raw alias",
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun `hello with known fingerprint refreshes address but preserves canonical name`() {
        val store = DiscoveredDevicesStore()
        val server = newServer(store = store)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                store.upsert(
                    Device(
                        name = "Host (2)",
                        host = "1.1.1.1",
                        port = 5000,
                        fingerprint = "fp-roaming",
                    ),
                )

                val response = client.post("http://localhost:$port/hello") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        PeerAnnouncement(
                            alias = "Host",
                            fingerprint = "fp-roaming",
                            port = 6000,
                            deviceType = DeviceType.Desktop,
                        ),
                    )
                }
                assertEquals(HttpStatusCode.OK, response.status)
            }
            assertEquals(1, store.devices.value.size, "no duplicate should be added")
            val entry = store.devices.value.first()
            assertEquals("Host (2)", entry.name, "canonical name must not be overwritten")
            assertEquals(6000, entry.port, "port must be refreshed to new value")
        } finally {
            client.close()
        }
    }

    @Test
    fun `hello inserts raw name for unknown peer, later mDNS upsert upgrades it to canonical`() {
        val store = DiscoveredDevicesStore()
        val server = newServer(store = store)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                // /hello arrives before any mDNS resolve: the peer is unknown, so its raw name is used.
                val response = client.post("http://localhost:$port/hello") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        PeerAnnouncement(
                            alias = "Host",
                            fingerprint = "fp-late",
                            port = 7000,
                            deviceType = DeviceType.Desktop,
                        ),
                    )
                }
                assertEquals(HttpStatusCode.OK, response.status)
            }
            assertEquals(
                "Host",
                store.devices.value
                    .first()
                    .name,
                "unknown peer takes the /hello raw name",
            )

            // mDNS resolves the same fingerprint and upserts the canonical name — full replace upgrades it.
            store.upsert(
                Device(name = "Host (3)", host = "192.168.1.5", port = 7000, fingerprint = "fp-late"),
            )
            assertEquals(1, store.devices.value.size, "same fingerprint must not duplicate")
            assertEquals(
                "Host (3)",
                store.devices.value
                    .first()
                    .name,
                "mDNS upsert must upgrade the raw name to the canonical one",
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun `hello uses TCP remoteAddress not body ip for device host`() {
        val store = DiscoveredDevicesStore()
        val server = newServer(store = store)
        val port = server.start()
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                client.post("http://localhost:$port/hello") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        PeerAnnouncement(
                            alias = "B",
                            fingerprint = "other",
                            port = 6000,
                            deviceType = DeviceType.Desktop,
                        ),
                    )
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
