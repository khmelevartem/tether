package com.tubetoast.tether.discovery

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MdnsDiscoveryRepublishTest {
    @Test
    fun `republish before start does not throw`() {
        val discovery = MdnsDiscovery(DiscoveredDevicesStore())
        discovery.republish("SomeName")
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

    @Test
    fun `two instances with same default name remain visible after one renames`() = runBlocking {
        val a = MdnsDiscovery(DiscoveredDevicesStore())
        val b = MdnsDiscovery(DiscoveredDevicesStore())
        try {
            a.start("Tether", 19110)
            b.start("Tether", 19111)

            withTimeout(10_000) {
                a.discoveredDevices.first { peers -> peers.any { it.port == 19111 } }
            }
            withTimeout(10_000) {
                b.discoveredDevices.first { peers -> peers.any { it.port == 19110 } }
            }

            a.republish("Tether-Renamed")

            val bAfterRename = withTimeout(15_000) {
                b.discoveredDevices.first { peers ->
                    peers.any { it.name == "Tether-Renamed" && it.port == 19110 }
                }
            }
            assertTrue(bAfterRename.any { it.name == "Tether-Renamed" && it.port == 19110 })

            val aAfterRename = withTimeout(10_000) {
                a.discoveredDevices.first { peers -> peers.any { it.port == 19111 } }
            }
            assertTrue(aAfterRename.any { it.port == 19111 })
        } finally {
            a.stop()
            b.stop()
        }
    }
}
