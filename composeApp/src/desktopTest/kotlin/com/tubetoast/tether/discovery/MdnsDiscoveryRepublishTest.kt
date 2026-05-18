package com.tubetoast.tether.discovery

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Same-default-name dedup after rename (DoD #147 bullet 6) is covered by:
//  - `DiscoveredDevicesStoreTest.upsert with same name and new address evicts stale entry`
//    (in-memory dedup contract, #90 regression)
//  - smoke-test Block 3 «Same-name discovery» (runtime cover for both Bonjour + JmDNS paths)
// Per #174: a JmDNS unit test with two same-name peers depends on conflict-rename probing
// timing (~750 ms/instance), unreliable on Linux CI.
class MdnsDiscoveryRepublishTest {
    @Test
    fun `republish before start is a no-op and adds no peers`() {
        val store = DiscoveredDevicesStore()
        val discovery = MdnsDiscovery(store)
        discovery.republish("SomeName")
        assertTrue(store.devices.value.isEmpty(), "republish without start must not add peers")
        discovery.stop()
    }

    @Test
    fun `peer sees new name after republish`() = runBlocking {
        val a = MdnsDiscovery(DiscoveredDevicesStore())
        val b = MdnsDiscovery(DiscoveredDevicesStore())
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
