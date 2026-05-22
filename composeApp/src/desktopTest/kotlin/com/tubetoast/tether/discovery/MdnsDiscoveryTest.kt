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

private fun testDiscovery() = MdnsDiscovery(DiscoveredDevicesStore())

// JmDNS delivers callbacks on real threads outside our CoroutineScope
@Suppress("ktlint:tether:no-run-blocking-in-tests")
class MdnsDiscoveryTest {
    @Test
    fun `stop before start does not throw`() {
        testDiscovery().stop()
    }

    @Test
    fun `start twice without stop throws IllegalStateException`() {
        val discovery = testDiscovery()
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
        val discovery = testDiscovery()
        discovery.start("StopEmits", 19003)
        discovery.stop()
        assertTrue(discovery.discoveredDevices.first().isEmpty())
    }

    @Test
    fun `two instances discover each other`() = runBlocking {
        val a = testDiscovery()
        val b = testDiscovery()
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
        val a = testDiscovery()
        val b = testDiscovery()
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
        val a = testDiscovery()
        val b = testDiscovery()
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
        val a = testDiscovery()
        val b = testDiscovery()
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
    fun `peer re-resolved with new port replaces old entry`() = runBlocking {
        val a = testDiscovery()
        val b = testDiscovery()
        try {
            a.start("PortChangeA", 19060)
            b.start("PortChangeB", 19061)

            withTimeout(10_000) {
                a.discoveredDevices.first { peers -> peers.any { it.name == "PortChangeB" && it.port == 19061 } }
            }

            b.stop()
            b.start("PortChangeB", 19062)

            val peersAfterRestart = withTimeout(15_000) {
                a.discoveredDevices.first { peers ->
                    peers.any { it.name == "PortChangeB" && it.port == 19062 }
                }
            }

            val portChangeBPeers = peersAfterRestart.filter { it.name == "PortChangeB" }
            assertEquals(1, portChangeBPeers.size, "expected exactly one entry for PortChangeB, got: $portChangeBPeers")
            assertEquals(19062, portChangeBPeers[0].port)
        } finally {
            a.stop()
            b.stop()
        }
    }

    @Test
    fun `late-joining peer is discovered after initial browse cycle`(): Unit = runBlocking {
        val a = testDiscovery()
        val b = testDiscovery()
        try {
            a.start("LateA", 19070)
            Thread.sleep(2_000)
            b.start("LateB", 19071)
            withTimeout(REQUERY_INITIAL_INTERVAL_MS + 10_000) {
                a.discoveredDevices.first { peers -> peers.any { it.port == 19071 } }
            }
        } finally {
            a.stop()
            b.stop()
        }
    }

    @Test
    fun `discovered device has correct host and port`() = runBlocking {
        val a = testDiscovery()
        val b = testDiscovery()
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
