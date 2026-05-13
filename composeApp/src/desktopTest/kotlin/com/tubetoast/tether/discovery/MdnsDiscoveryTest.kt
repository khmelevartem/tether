package com.tubetoast.tether.discovery

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MdnsDiscoveryTest {
    @Test
    fun `stop before start does not throw`() {
        MdnsDiscovery(DiscoveredDevicesStore()).stop()
    }

    @Test
    fun `start twice without stop throws IllegalStateException`() {
        val discovery = MdnsDiscovery(DiscoveredDevicesStore())
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
        val discovery = MdnsDiscovery(DiscoveredDevicesStore())
        discovery.start("StopEmits", 19003)
        discovery.stop()
        assertTrue(discovery.discoveredDevices.first().isEmpty())
    }

    @Test
    fun `two instances discover each other`() = runBlocking {
        val a = MdnsDiscovery(DiscoveredDevicesStore())
        val b = MdnsDiscovery(DiscoveredDevicesStore())
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
        val a = MdnsDiscovery(DiscoveredDevicesStore())
        val b = MdnsDiscovery(DiscoveredDevicesStore())
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
        val a = MdnsDiscovery(DiscoveredDevicesStore())
        val b = MdnsDiscovery(DiscoveredDevicesStore())
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
        val a = MdnsDiscovery(DiscoveredDevicesStore())
        val b = MdnsDiscovery(DiscoveredDevicesStore())
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
        val a = MdnsDiscovery(DiscoveredDevicesStore())
        val b = MdnsDiscovery(DiscoveredDevicesStore())
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

    // NOTE: the "JmDNS renames on conflict" test was removed because it tested JmDNS
    // internal conflict-probing behaviour rather than our own code. Our self-filter uses
    // IP+port (not name), so it is rename-proof by construction — no renaming scenario
    // can break it. The test was also consistently flaky: same-machine JmDNS conflict
    // probing is timing-dependent and cannot reliably complete within a fixed timeout.
    // The correctness guarantee is documented in MdnsDiscovery.jvm.kt instead.

    // Skipped on macOS: this test injects a TestDispatcher into MdnsDiscoveryJmdns, and
    // JmDNS on macOS cannot resolve IPv4 from mDNSResponder for local loopback services.
    @Test
    fun `late-joining peer is discovered after initial browse cycle`() = runTest {
        org.junit.Assume.assumeFalse("JmDNS IPv4 resolution unavailable on macOS", isMacOs())
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val a = MdnsDiscoveryJmdns(DiscoveredDevicesStore(), testDispatcher)
        val b = MdnsDiscoveryJmdns(DiscoveredDevicesStore(), testDispatcher)
        try {
            a.start("LateA", 19070)
            // JmDNS's ServiceResolver fires 3 PTR queries over ~675 ms of real time.
            // Thread.sleep keeps us in real time so the burst finishes before b joins.
            Thread.sleep(1_000)
            b.start("LateB", 19071)
            // JmDNS probes the service name before advertising (~3 × 250 ms). Wait for
            // b to be reachable on the network before triggering a's re-query.
            Thread.sleep(1_000)
            // Advance virtual clock past the initial re-query interval: triggers
            // addServiceListener in a's re-query coroutine, which re-arms ServiceResolver.
            advanceTimeBy(REQUERY_INITIAL_INTERVAL_MS + 1)
            // Poll with real time — discoveredDevices is updated from JmDNS's own threads
            // and coroutine withTimeout would use virtual clock (routed through testScheduler).
            val deadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < deadline) {
                if (a.discoveredDevices.value.any { it.port == 19071 }) break
                Thread.sleep(100)
            }
            val lateB = a.discoveredDevices.value.filter { it.port == 19071 }
            assertEquals(1, lateB.size, "LateB not discovered within 10 s after re-query")
            assertEquals(19071, lateB[0].port)
        } finally {
            a.stop()
            b.stop()
        }
    }

    @Test
    fun `three instances with same name each discover two others`(): Unit = runBlocking {
        val a = MdnsDiscovery(DiscoveredDevicesStore())
        val b = MdnsDiscovery(DiscoveredDevicesStore())
        val c = MdnsDiscovery(DiscoveredDevicesStore())
        try {
            // JmDNS probes for ~750 ms before committing a name. Starting three same-name
            // instances simultaneously makes their probes overlap and JmDNS' conflict
            // resolution becomes racy on slow runners. Stagger to keep each probe clean.
            a.start("SameName", 19080)
            Thread.sleep(1_000)
            b.start("SameName", 19081)
            Thread.sleep(1_000)
            c.start("SameName", 19082)

            val timeoutMs = 30_000L
            coroutineScope {
                listOf(
                    async { awaitPorts(a, 19081, 19082, timeoutMs) },
                    async { awaitPorts(b, 19080, 19082, timeoutMs) },
                    async { awaitPorts(c, 19080, 19081, timeoutMs) },
                ).awaitAll()
            }
        } finally {
            a.stop()
            b.stop()
            c.stop()
        }
    }

    private suspend fun awaitPorts(discovery: DeviceDiscovery, p1: Int, p2: Int, timeoutMs: Long) {
        withTimeout(timeoutMs) {
            discovery.discoveredDevices.first { peers ->
                val ports = peers.map { it.port }
                p1 in ports && p2 in ports
            }
        }
    }

    @Test
    fun `discovered device has correct host and port`() = runBlocking {
        val a = MdnsDiscovery(DiscoveredDevicesStore())
        val b = MdnsDiscovery(DiscoveredDevicesStore())
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

private fun isMacOs() = System
    .getProperty("os.name")
    .orEmpty()
    .lowercase()
    .startsWith("mac")
