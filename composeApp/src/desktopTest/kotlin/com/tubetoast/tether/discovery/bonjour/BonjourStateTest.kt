package com.tubetoast.tether.discovery.bonjour

import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.protocol.Device
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BonjourStateTest {
    private class RecordingSink : BonjourState.Sink {
        val opened = mutableListOf<Pair<String, String>>() // tag → arg

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

    private fun makeStateWithSink(deviceName: String): Triple<BonjourState, DiscoveredDevicesStore, RecordingSink> {
        val store = DiscoveredDevicesStore()
        val sink = RecordingSink()
        return Triple(BonjourState(deviceName, store, sink), store, sink)
    }

    @Test
    fun `device emits only when both port and ip present`() {
        val (state, store, _) = makeStateWithSink("self")

        state.onBrowseAdd("PeerA", interfaceIndex = 0)
        assertEquals(0, store.devices.value.size, "no device until resolve+addrinfo")

        state.onResolved("PeerA", "peera.local", port = 19999)
        assertEquals(0, store.devices.value.size, "still no device — IP missing")

        state.onAddrInfoFound("PeerA", "10.0.0.5", isAdd = true)
        assertEquals(listOf(Device("PeerA@10.0.0.5:19999", "PeerA", "10.0.0.5", 19999)), store.devices.value)
    }

    @Test
    fun `resolve with new port replaces existing device`() {
        val (state, store, _) = makeStateWithSink("self")

        state.onBrowseAdd("PeerA", 0)
        state.onResolved("PeerA", "peera.local", 19999)
        state.onAddrInfoFound("PeerA", "10.0.0.5", isAdd = true)
        assertEquals(
            19999,
            store.devices.value
                .single()
                .port,
        )

        state.onResolved("PeerA", "peera.local", 20001)
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
        val (state, store, sink) = makeStateWithSink("self")

        state.onBrowseAdd("PeerA", 0)
        state.onResolved("PeerA", "peera.local", 19999)
        state.onAddrInfoFound("PeerA", "10.0.0.5", isAdd = true)
        assertEquals(1, store.devices.value.size)

        state.onBrowseRemove("PeerA")
        assertTrue(store.devices.value.isEmpty(), "device removed")
        assertTrue(sink.opened.contains("closeResolve" to "PeerA"))
        assertTrue(sink.opened.contains("closeAddrInfo" to "PeerA"))

        // After remove, a new add-resolve-addrinfo cycle works again.
        state.onBrowseAdd("PeerA", 0)
        state.onResolved("PeerA", "peera.local", 22222)
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
        val (state, store, _) = makeStateWithSink("self")

        state.onBrowseAdd("PeerA", 0)
        state.onResolved("PeerA", "peera.local", 19999)
        state.onAddrInfoFound("PeerA", "10.0.0.5", isAdd = true)
        val snapshotBefore = store.devices.value

        state.onAddrInfoFound("PeerA", "10.0.0.5", isAdd = false)
        // Device entry stays — Browse-remove is the canonical "peer gone" signal.
        assertEquals(snapshotBefore, store.devices.value)
    }

    @Test
    fun `self filter ignores configured device name`() {
        val (state, store, sink) = makeStateWithSink("Self")

        state.onBrowseAdd("Self", 0)
        state.onResolved("Self", "self.local", 18000)
        state.onAddrInfoFound("Self", "10.0.0.1", isAdd = true)
        assertEquals(0, store.devices.value.size, "own service must never appear in devices list")
        assertTrue(sink.opened.none { it.first == "openResolve" }, "no resolve for self")
    }

    @Test
    fun `late Resolved arriving after BrowseRemove is dropped`() {
        // Reproduces the small race the reviewer flagged: with Channel.UNLIMITED, a
        // Resolved callback can be queued before BrowseRemove and then consumed after
        // it. Without the membership gate, this resurrects the peer.
        val (state, store, sink) = makeStateWithSink("self")

        state.onBrowseAdd("PeerA", 0)
        state.onResolved("PeerA", "peera.local", 19999)
        state.onAddrInfoFound("PeerA", "10.0.0.5", isAdd = true)
        state.onBrowseRemove("PeerA")
        assertTrue(store.devices.value.isEmpty(), "device removed by BrowseRemove")

        // Late event — should be dropped, not re-add the peer or re-open subordinates.
        val openedBefore = sink.opened.size
        state.onResolved("PeerA", "peera.local", 20000)
        assertTrue(store.devices.value.isEmpty(), "stale Resolved must not resurrect peer")
        assertEquals(openedBefore, sink.opened.size, "stale Resolved must not open new subordinates")
    }

    @Test
    fun `late AddrInfoFound arriving after BrowseRemove is dropped`() {
        val (state, store, _) = makeStateWithSink("self")

        state.onBrowseAdd("PeerA", 0)
        state.onResolved("PeerA", "peera.local", 19999)
        state.onAddrInfoFound("PeerA", "10.0.0.5", isAdd = true)
        state.onBrowseRemove("PeerA")

        state.onAddrInfoFound("PeerA", "10.0.0.6", isAdd = true)
        assertTrue(store.devices.value.isEmpty(), "stale AddrInfoFound must not resurrect peer")
    }

    @Test
    fun `canonical name from RegisterReply removes self entry that slipped in`() {
        // mDNSResponder may rename us on conflict ("Self" → "Self (2)"). Until the
        // canonical name is known, the configured name is used as a self-filter, so
        // a device under the renamed name can briefly appear in the list.
        val (state, store, _) = makeStateWithSink("Self")

        state.onBrowseAdd("Self (2)", 0)
        state.onResolved("Self (2)", "self.local", 18000)
        state.onAddrInfoFound("Self (2)", "10.0.0.1", isAdd = true)
        assertEquals(1, store.devices.value.size, "before canonical name we publish the renamed entry")

        state.ownNameAssigned("Self (2)")
        assertTrue(store.devices.value.isEmpty(), "self entry removed once canonical name known")
    }
}
