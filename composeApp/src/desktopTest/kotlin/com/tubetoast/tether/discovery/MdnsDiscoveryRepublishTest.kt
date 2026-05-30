package com.tubetoast.tether.discovery

import com.tubetoast.tether.identity.DeviceIdentityStore
import com.tubetoast.tether.preferences.TempDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun testDiscovery(
    store: DiscoveredDevicesStore = DiscoveredDevicesStore(),
): MdnsDiscovery {
    val temp = TempDataStore()
    return MdnsDiscovery(store, DeviceIdentityStore(temp.dataStore))
}

class MdnsDiscoveryRepublishTest {
    @Test
    fun `republish before start is a no-op and adds no peers`() {
        val store = DiscoveredDevicesStore()
        val discovery = testDiscovery(store)
        discovery.republish("SomeName")
        assertTrue(store.devices.value.isEmpty(), "republish without start must not add peers")
        discovery.stop()
    }

    // JmDNS callbacks fire on OS-owned threads outside runTest's virtual clock
    @Suppress("ktlint:tether:no-run-blocking-in-tests")
    @Test
    fun `peer sees new name after republish`() = runBlocking {
        val a = testDiscovery()
        val b = testDiscovery()
        try {
            a.start("NameA-Before", 19100)
            b.start("NameB-Observer", 19101)

            withTimeout(10_000) {
                b.discoveredDevices.first { peers -> peers.any { it.name == "NameA-Before" } }
            }

            a.republish("NameA-After")

            val updated = withTimeout(15_000) {
                b.discoveredDevices.first { peers -> peers.any { it.name == "NameA-After" } }
            }
            val renamedPeers = updated.filter { it.name == "NameA-After" }
            assertEquals(1, renamedPeers.size, "expected exactly one entry for NameA-After")
            assertEquals(19100, renamedPeers[0].port)
        } finally {
            a.stop()
            b.stop()
        }
    }
}
