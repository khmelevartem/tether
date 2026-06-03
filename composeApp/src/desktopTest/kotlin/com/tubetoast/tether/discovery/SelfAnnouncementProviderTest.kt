package com.tubetoast.tether.discovery

import com.tubetoast.tether.config.DeviceNamePersistence
import com.tubetoast.tether.config.DeviceNameStore
import com.tubetoast.tether.identity.DeviceIdentityStore
import com.tubetoast.tether.identity.EphemeralFingerprintPersistence
import com.tubetoast.tether.network.FileServer
import com.tubetoast.tether.preferences.TempDataStore
import com.tubetoast.tether.protocol.DeviceType
import com.tubetoast.tether.security.DefaultTrustedDeviceStore
import com.tubetoast.tether.security.DeviceKeyPair
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// FileServer uses real CIO which hardcodes real-thread dispatchers.
@Suppress("ktlint:tether:no-run-blocking-in-tests")
class SelfAnnouncementProviderTest {
    private val cleanupStores = mutableListOf<TempDataStore>()
    private var startedServer: FileServer? = null

    @AfterTest
    fun teardown() {
        startedServer?.stop()
        startedServer = null
        cleanupStores.forEach { it.tearDown() }
        cleanupStores.clear()
    }

    private fun newServer(): FileServer {
        val configDir = Files.createTempDirectory("tether-sap-test-keys").toFile()
        val downloadsDir = Files.createTempDirectory("tether-sap-test-dl").toFile()
        val temp = TempDataStore().also { cleanupStores += it }
        return FileServer(
            configuredPort = 0,
            downloadsDir = downloadsDir,
            trustedDeviceStore = DefaultTrustedDeviceStore(temp.dataStore),
            deviceKeyPair = DeviceKeyPair(configDir),
            deviceIdentityStore = DeviceIdentityStore(EphemeralFingerprintPersistence()),
            discoveredDevicesStore = DiscoveredDevicesStore(),
        ).also { startedServer = it }
    }

    private fun nameStore(name: String): DeviceNameStore {
        val persistence = object : DeviceNamePersistence {
            override suspend fun read() = name
            override suspend fun write(value: String) = Unit
        }
        return DeviceNameStore(persistence)
    }

    private fun canonicalSource(name: String?): CanonicalNameSource = object : CanonicalNameSource {
        override val ownPublishedName: StateFlow<String?> = MutableStateFlow(name)
    }

    @Test
    fun `alias falls back to nameStore when ownPublishedName is null`() = runBlocking {
        val source = canonicalSource(null)
        assertNull(source.ownPublishedName.value)
        val store = nameStore("MyDevice")
        store.init()
        val identityStore = DeviceIdentityStore(EphemeralFingerprintPersistence())
        val server = newServer()
        server.start()
        val provider = DefaultSelfAnnouncementProvider(store, server, identityStore, DeviceType.Desktop, source)
        val announcement = provider.get()
        assertEquals("MyDevice", announcement.alias, "alias must come from nameStore when ownPublishedName is null")
    }

    @Test
    fun `alias uses ownPublishedName when set, ignoring nameStore`() = runBlocking {
        val source = canonicalSource("BaseName (2)")
        val store = nameStore("BaseName")
        store.init()
        val identityStore = DeviceIdentityStore(EphemeralFingerprintPersistence())
        val server = newServer()
        server.start()
        val provider = DefaultSelfAnnouncementProvider(store, server, identityStore, DeviceType.Desktop, source)
        assertEquals("BaseName (2)", provider.get().alias, "canonical name must win over nameStore name")
    }

    @Test
    fun `alias falls back to nameStore when ownPublishedName stays null past the timeout`() = runTest {
        val stuck = object : CanonicalNameSource {
            override val ownPublishedName: StateFlow<String?> = MutableStateFlow(null)
        }
        val store = nameStore("FallbackDevice")
        store.init()
        val identityStore = DeviceIdentityStore(EphemeralFingerprintPersistence())
        // FileServer not started — port reads 0, which is fine for this alias-only assertion.
        val configDir = Files.createTempDirectory("tether-sap-timeout-keys").toFile()
        val downloadsDir = Files.createTempDirectory("tether-sap-timeout-dl").toFile()
        val temp = TempDataStore().also { cleanupStores += it }
        val server = FileServer(
            configuredPort = 0,
            downloadsDir = downloadsDir,
            trustedDeviceStore = DefaultTrustedDeviceStore(temp.dataStore),
            deviceKeyPair = DeviceKeyPair(configDir),
            deviceIdentityStore = DeviceIdentityStore(EphemeralFingerprintPersistence()),
            discoveredDevicesStore = DiscoveredDevicesStore(),
        )
        val provider = DefaultSelfAnnouncementProvider(store, server, identityStore, DeviceType.Desktop, stuck)
        val announcement = provider.get()
        assertEquals("FallbackDevice", announcement.alias, "must fall back to nameStore when ownPublishedName never becomes non-null")
    }
}
