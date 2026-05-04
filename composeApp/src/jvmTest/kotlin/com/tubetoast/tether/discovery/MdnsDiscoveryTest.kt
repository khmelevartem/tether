package com.tubetoast.tether.discovery

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

            // Await both directions in parallel — cuts wall-clock time roughly in half.
            val (seenByA, seenByB) = coroutineScope {
                val dA = async {
                    withTimeout(10_000) {
                        a.discoveredDevices.first { peers ->
                            peers.any {
                                it.name ==
                                    "PeerB"
                            }
                        }
                    }
                }
                val dB = async {
                    withTimeout(10_000) {
                        b.discoveredDevices.first { peers ->
                            peers.any {
                                it.name ==
                                    "PeerA"
                            }
                        }
                    }
                }
                dA.await() to dB.await()
            }

            // Filter to known test peers to stay resilient against external mDNS services.
            val peerBinA = seenByA.filter { it.name == "PeerB" }
            val peerAinB = seenByB.filter { it.name == "PeerA" }

            assertEquals(1, peerBinA.size)
            assertEquals(19011, peerBinA[0].port)

            assertEquals(1, peerAinB.size)
            assertEquals(19010, peerAinB[0].port)
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

            val (seenByA, seenByB) = coroutineScope {
                val dA = async {
                    withTimeout(10_000) {
                        a.discoveredDevices.first { peers ->
                            peers.any {
                                it.name ==
                                    "SelfB"
                            }
                        }
                    }
                }
                val dB = async {
                    withTimeout(10_000) {
                        b.discoveredDevices.first { peers ->
                            peers.any {
                                it.name ==
                                    "SelfA"
                            }
                        }
                    }
                }
                dA.await() to dB.await()
            }

            assertTrue(seenByA.none { it.name == "SelfA" })
            assertTrue(seenByB.none { it.name == "SelfB" })
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
            val seenAfterRestart = withTimeout(10_000) {
                a.discoveredDevices.first { peers -> peers.any { it.name == "RestartB" } }
            }
            // Filter to the known test peer — external services may also be present.
            assertEquals(1, seenAfterRestart.filter { it.name == "RestartB" }.size)
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
    fun `instance does not discover itself when JmDNS renames due to name conflict`() = runBlocking {
        // When two instances register the same name, JmDNS renames one (e.g. "Foo" → "Foo (2)").
        // Self-filter is IP+port based, so it must work correctly regardless of any rename.
        // Discovery runs in parallel because JmDNS conflict probing adds extra round-trips.
        val a = MdnsDiscovery()
        val b = MdnsDiscovery()
        try {
            a.start("ConflictName", 19060)
            b.start("ConflictName", 19061) // JmDNS will rename to "ConflictName (2)"

            val (seenByA, seenByB) = coroutineScope {
                val dA =
                    async {
                        withTimeout(
                            15_000,
                        ) { a.discoveredDevices.first { peers -> peers.any { it.port == 19061 } } }
                    }
                val dB =
                    async {
                        withTimeout(
                            15_000,
                        ) { b.discoveredDevices.first { peers -> peers.any { it.port == 19060 } } }
                    }
                dA.await() to dB.await()
            }

            assertTrue(seenByA.none { it.port == 19060 }, "A must not discover itself (port 19060)")
            assertTrue(seenByB.none { it.port == 19061 }, "B must not discover itself (port 19061)")
        } finally {
            a.stop()
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
