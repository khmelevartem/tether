package com.tubetoast.tether.discovery.bonjour

import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.protocol.Device
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration

class BonjourStateTest {
    private class RecordingSink : BonjourState.Sink {
        val opened = mutableListOf<Pair<String, String>>()

        override fun openResolve(name: String, interfaceIndex: Int) {
            opened += "openResolve" to "$name@$interfaceIndex"
        }

        override fun closeResolve(name: String) {
            opened += "closeResolve" to name
        }

        override fun openAddrInfo(name: String, hostname: String) {
            opened += "openAddrInfo" to "$name@$hostname"
        }

        override fun closeAddrInfo(name: String) {
            opened += "closeAddrInfo" to name
        }
    }

    // staleGrace=ZERO so mDNS serviceLost (BrowseRemove) always evicts in unit tests.
    private val store = DiscoveredDevicesStore(staleGrace = Duration.ZERO)
    private val sink = RecordingSink()
    private val state = BonjourState(store, sink, ownFingerprint = "") { _, _ -> false }

    @Test
    fun `device emits only when both port and ip present`() {
        state.onBrowseAdd("PeerA", interfaceIndex = 0)
        assertEquals(0, store.devices.value.size, "no device until resolve+addrinfo")

        state.onResolved("PeerA", "peera.local", port = 19999, peerFingerprint = "fpA")
        assertEquals(0, store.devices.value.size, "still no device — IP missing")

        state.onAddrInfoFound("PeerA", "10.0.0.5", isAdd = true)
        assertEquals(listOf(Device("PeerA", "10.0.0.5", 19999, fingerprint = "fpA")), store.devices.value)
    }

    @Test
    fun `resolve with new port replaces existing device`() {
        state.onBrowseAdd("PeerA", 0)
        state.onResolved("PeerA", "peera.local", 19999, peerFingerprint = "fpA")
        state.onAddrInfoFound("PeerA", "10.0.0.5", isAdd = true)
        assertEquals(
            19999,
            store.devices.value
                .single()
                .port,
        )

        state.onResolved("PeerA", "peera.local", 20001, peerFingerprint = "fpA")
        assertEquals(
            20001,
            store.devices.value
                .single()
                .port,
        )
        assertEquals(1, store.devices.value.size)
    }

    // The store collapses multi-rename announces to one canonical entry (Rule 1), so a
    // BrowseRemove for any intermediate (already superseded) name must be a no-op for the
    // live entry.
    @Test
    fun `browse remove for an intermediate rename name does not evict the live canonical entry`() {
        // One peer announces under "PeerA" (the intermediate name) and is then
        // canonicalised by mDNSResponder to "PeerA (2)". Both BrowseAdds carry the same fingerprint.
        state.onBrowseAdd("PeerA", 0)
        state.onResolved("PeerA", "peera.local", 19999, peerFingerprint = "fpA")
        state.onAddrInfoFound("PeerA", "10.0.0.5", isAdd = true)

        state.onBrowseAdd("PeerA (2)", 0)
        state.onResolved("PeerA (2)", "peera.local", 19999, peerFingerprint = "fpA")
        state.onAddrInfoFound("PeerA (2)", "10.0.0.5", isAdd = true)
        assertEquals(
            "PeerA (2)",
            store.devices.value
                .single()
                .name,
            "Rule 1 collapses to the canonical name",
        )

        // mDNSResponder retracts the intermediate name.
        state.onBrowseRemove("PeerA")
        assertEquals(
            1,
            store.devices.value.size,
            "the live canonical entry must survive an intermediate-name BrowseRemove",
        )
        assertEquals(
            "PeerA (2)",
            store.devices.value
                .single()
                .name,
        )
    }

    @Test
    fun `browse remove cleans up device, pending state, and active subordinates`() {
        state.onBrowseAdd("PeerA", 0)
        state.onResolved("PeerA", "peera.local", 19999, peerFingerprint = "fpA")
        state.onAddrInfoFound("PeerA", "10.0.0.5", isAdd = true)
        assertEquals(1, store.devices.value.size)

        state.onBrowseRemove("PeerA")
        assertTrue(store.devices.value.isEmpty(), "device removed")
        assertTrue(sink.opened.contains("closeResolve" to "PeerA"))
        assertTrue(sink.opened.contains("closeAddrInfo" to "PeerA"))

        state.onBrowseAdd("PeerA", 0)
        state.onResolved("PeerA", "peera.local", 22222, peerFingerprint = "fpA2")
        state.onAddrInfoFound("PeerA", "10.0.0.6", isAdd = true)
        assertEquals(1, store.devices.value.size)
        assertEquals(
            22222,
            store.devices.value
                .single()
                .port,
        )
    }

    @Test
    fun `addrInfo with isAdd=false drops pending IP without removing device`() {
        state.onBrowseAdd("PeerA", 0)
        state.onResolved("PeerA", "peera.local", 19999, peerFingerprint = "fpA")
        state.onAddrInfoFound("PeerA", "10.0.0.5", isAdd = true)
        val snapshotBefore = store.devices.value
        assertEquals(1, snapshotBefore.size, "device must be in the store before isAdd=false arrives")

        state.onAddrInfoFound("PeerA", "10.0.0.5", isAdd = false)
        assertEquals(snapshotBefore, store.devices.value)
    }

    @Test
    fun `self filter by host and port — self never enters the store`() {
        val state = BonjourState(store, sink, ownFingerprint = "") { host, port -> host == "10.0.0.1" && port == 18000 }

        state.onBrowseAdd("Self", 0)
        state.onResolved("Self", "self.local", 18000, peerFingerprint = "fpSelf")
        state.onAddrInfoFound("Self", "10.0.0.1", isAdd = true)
        assertEquals(0, store.devices.value.size, "own service must never appear in devices list")
    }

    @Test
    fun `self filter — different port on same host is not self`() {
        val state = BonjourState(store, sink, ownFingerprint = "") { host, port -> host == "10.0.0.1" && port == 18000 }

        state.onBrowseAdd("Peer", 0)
        state.onResolved("Peer", "peer.local", 19000, peerFingerprint = "fpPeer")
        state.onAddrInfoFound("Peer", "10.0.0.1", isAdd = true)
        assertEquals(1, store.devices.value.size, "peer on same host but different port must be kept")
    }

    @Test
    fun `self filter is name-independent — renamed service on own host+port is excluded`() {
        val state = BonjourState(store, sink, ownFingerprint = "") { host, port -> host == "10.0.0.1" && port == 18000 }

        state.onBrowseAdd("Self (2)", 0)
        state.onResolved("Self (2)", "self.local", 18000, peerFingerprint = "fpSelfRenamed")
        state.onAddrInfoFound("Self (2)", "10.0.0.1", isAdd = true)
        assertEquals(0, store.devices.value.size, "renamed self must still be excluded")
    }

    // Self-suppression by fingerprint stays independent of (host, port) — the only test that
    // exercises BonjourState.emitIfReady's `peerFingerprint == ownFingerprint` gate.
    @Test
    fun `self filter by fingerprint — own fingerprint never enters the store`() {
        val state = BonjourState(store, sink, ownFingerprint = "fpSelf") { _, _ -> false }

        state.onBrowseAdd("Self", 0)
        state.onResolved("Self", "self.local", 18000, peerFingerprint = "fpSelf")
        state.onAddrInfoFound("Self", "10.0.0.1", isAdd = true)
        assertEquals(0, store.devices.value.size, "own fingerprint must never enter the store")
    }

    @Test
    fun `late Resolved arriving after BrowseRemove is dropped`() {
        state.onBrowseAdd("PeerA", 0)
        state.onResolved("PeerA", "peera.local", 19999, peerFingerprint = "fpA")
        state.onAddrInfoFound("PeerA", "10.0.0.5", isAdd = true)
        assertEquals(1, store.devices.value.size)
        state.onBrowseRemove("PeerA")
        assertTrue(store.devices.value.isEmpty(), "device removed by BrowseRemove")

        val openedBefore = sink.opened.size
        state.onResolved("PeerA", "peera.local", 20000, peerFingerprint = "fpA")
        assertTrue(store.devices.value.isEmpty(), "stale Resolved must not resurrect peer")
        assertEquals(openedBefore, sink.opened.size, "stale Resolved must not open new subordinates")
    }

    @Test
    fun `late AddrInfoFound arriving after BrowseRemove is dropped`() {
        state.onBrowseAdd("PeerA", 0)
        state.onResolved("PeerA", "peera.local", 19999, peerFingerprint = "fpA")
        state.onAddrInfoFound("PeerA", "10.0.0.5", isAdd = true)
        assertEquals(1, store.devices.value.size)
        state.onBrowseRemove("PeerA")

        state.onAddrInfoFound("PeerA", "10.0.0.6", isAdd = true)
        assertTrue(store.devices.value.isEmpty(), "stale AddrInfoFound must not resurrect peer")
    }
}
