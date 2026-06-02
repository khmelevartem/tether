package com.tubetoast.tether.discovery.bonjour

import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.protocol.Device
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    private val store = DiscoveredDevicesStore()
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
        state.onResolved("PeerA", "peera.local", 19999, peerFingerprint = null)
        state.onAddrInfoFound("PeerA", "10.0.0.5", isAdd = true)
        val snapshotBefore = store.devices.value

        state.onAddrInfoFound("PeerA", "10.0.0.5", isAdd = false)
        assertEquals(snapshotBefore, store.devices.value)
    }

    @Test
    fun `self filter by host and port — self never enters the store`() {
        val state = BonjourState(store, sink, ownFingerprint = "") { host, port -> host == "10.0.0.1" && port == 18000 }

        state.onBrowseAdd("Self", 0)
        state.onResolved("Self", "self.local", 18000, peerFingerprint = null)
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
        state.onResolved("Self (2)", "self.local", 18000, peerFingerprint = null)
        state.onAddrInfoFound("Self (2)", "10.0.0.1", isAdd = true)
        assertEquals(0, store.devices.value.size, "renamed self must still be excluded")
    }

    @Test
    fun `late Resolved arriving after BrowseRemove is dropped`() {
        state.onBrowseAdd("PeerA", 0)
        state.onResolved("PeerA", "peera.local", 19999, peerFingerprint = null)
        state.onAddrInfoFound("PeerA", "10.0.0.5", isAdd = true)
        state.onBrowseRemove("PeerA")
        assertTrue(store.devices.value.isEmpty(), "device removed by BrowseRemove")

        val openedBefore = sink.opened.size
        state.onResolved("PeerA", "peera.local", 20000, peerFingerprint = null)
        assertTrue(store.devices.value.isEmpty(), "stale Resolved must not resurrect peer")
        assertEquals(openedBefore, sink.opened.size, "stale Resolved must not open new subordinates")
    }

    @Test
    fun `late AddrInfoFound arriving after BrowseRemove is dropped`() {
        state.onBrowseAdd("PeerA", 0)
        state.onResolved("PeerA", "peera.local", 19999, peerFingerprint = null)
        state.onAddrInfoFound("PeerA", "10.0.0.5", isAdd = true)
        state.onBrowseRemove("PeerA")

        state.onAddrInfoFound("PeerA", "10.0.0.6", isAdd = true)
        assertTrue(store.devices.value.isEmpty(), "stale AddrInfoFound must not resurrect peer")
    }
}
