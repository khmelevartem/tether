package com.tubetoast.tether.discovery

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MdnsDiscoveryTest {
    @Test
    fun `stop before start does not throw`() {
        MdnsDiscovery().stop()
    }

    @Test
    fun `start twice without stop throws IllegalStateException`() {
        val discovery = MdnsDiscovery()
        discovery.start("DoubleStart", 19001)
        try {
            assertFailsWith<IllegalStateException> {
                discovery.start("DoubleStart2", 19002)
            }
        } finally {
            discovery.stop()
        }
    }

    @Test
    fun `stop emits empty list`() = runBlocking {
        val discovery = MdnsDiscovery()
        discovery.start("StopEmits", 19003)
        discovery.stop()
        assertTrue(discovery.discoveredDevices.first().isEmpty())
    }

    @Test
    fun `two instances discover each other`() = runBlocking {
        val a = MdnsDiscovery()
        val b = MdnsDiscovery()
        try {
            a.start("PeerA", 19010)
            b.start("PeerB", 19011)

            withTimeout(10_000) {
                a.discoveredDevices.first { peers -> peers.any { it.name == "PeerB" } }
            }
            withTimeout(10_000) {
                b.discoveredDevices.first { peers -> peers.any { it.name == "PeerA" } }
            }

            val seenByA = a.discoveredDevices.first()
            val seenByB = b.discoveredDevices.first()

            assertEquals(1, seenByA.size)
            assertEquals("PeerB", seenByA[0].name)
            assertEquals(19011, seenByA[0].port)

            assertEquals(1, seenByB.size)
            assertEquals("PeerA", seenByB[0].name)
            assertEquals(19010, seenByB[0].port)
        } finally {
            a.stop()
            b.stop()
        }
    }

    @Test
    fun `instances do not discover themselves`() = runBlocking {
        val a = MdnsDiscovery()
        val b = MdnsDiscovery()
        try {
            a.start("SelfA", 19020)
            b.start("SelfB", 19021)

            withTimeout(10_000) {
                a.discoveredDevices.first { peers -> peers.any { it.name == "SelfB" } }
            }
            withTimeout(10_000) {
                b.discoveredDevices.first { peers -> peers.any { it.name == "SelfA" } }
            }

            assertTrue(a.discoveredDevices.first().none { it.name == "SelfA" })
            assertTrue(b.discoveredDevices.first().none { it.name == "SelfB" })
        } finally {
            a.stop()
            b.stop()
        }
    }

    @Test
    fun `restart — stop then start works correctly`() = runBlocking {
        val a = MdnsDiscovery()
        val b = MdnsDiscovery()
        try {
            a.start("RestartA", 19050)
            b.start("RestartB", 19051)

            withTimeout(10_000) {
                a.discoveredDevices.first { peers -> peers.any { it.name == "RestartB" } }
            }

            a.stop()
            assertTrue(a.discoveredDevices.first().isEmpty())

            a.start("RestartA", 19050)
            withTimeout(10_000) {
                a.discoveredDevices.first { peers -> peers.any { it.name == "RestartB" } }
            }
            assertEquals(1, a.discoveredDevices.first().size)
        } finally {
            a.stop()
            b.stop()
        }
    }

    @Test
    fun `stop clears previously discovered peers`() = runBlocking {
        val a = MdnsDiscovery()
        val b = MdnsDiscovery()
        try {
            a.start("ClearA", 19030)
            b.start("ClearB", 19031)

            withTimeout(10_000) {
                a.discoveredDevices.first { peers -> peers.any { it.name == "ClearB" } }
            }

            a.stop()
            assertTrue(a.discoveredDevices.first().isEmpty())
        } finally {
            b.stop()
        }
    }

    @Test
    fun `discovered device has correct host and port`() = runBlocking {
        val a = MdnsDiscovery()
        val b = MdnsDiscovery()
        try {
            a.start("HostA", 19040)
            b.start("HostB", 19041)

            val peers = withTimeout(10_000) {
                a.discoveredDevices.first { peers -> peers.any { it.name == "HostB" } }
            }

            val device = peers.first { it.name == "HostB" }
            assertTrue(device.host.matches(Regex("""\d+\.\d+\.\d+\.\d+""")), "host should be IPv4: ${device.host}")
            assertEquals(19041, device.port)
        } finally {
            a.stop()
            b.stop()
        }
    }
}
