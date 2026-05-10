package com.tubetoast.tether.discovery.bonjour

import com.tubetoast.tether.protocol.Device
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BonjourStateTest {
    private class RecordingSink : BonjourState.Sink {
        val opened = mutableListOf<Pair<String, String>>() // tag → arg

        var devices: List<Device> = emptyList()
            private set

        var publishCount: Int = 0
            private set

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

        override fun publishDevices(devices: List<Device>) {
            this.devices = devices
            publishCount++
        }
    }

    @Test
    fun `device emits only when both port and ip present`() {
        val sink = RecordingSink()
        val state = BonjourState(deviceName = "self", sink = sink)

        state.onBrowseAdd("PeerA", interfaceIndex = 0)
        assertEquals(0, sink.publishCount, "no device until resolve+addrinfo")

        state.onResolved("PeerA", "peera.local", port = 19999)
        assertEquals(0, sink.publishCount, "still no device — IP missing")

        state.onAddrInfoFound("PeerA", "10.0.0.5", isAdd = true)
        assertEquals(1, sink.publishCount)
        assertEquals(listOf(Device("PeerA@10.0.0.5:19999", "PeerA", "10.0.0.5", 19999)), sink.devices)
    }

    @Test
    fun `resolve with new port replaces existing device`() {
        val sink = RecordingSink()
        val state = BonjourState("self", sink)

        state.onBrowseAdd("PeerA", 0)
        state.onResolved("PeerA", "peera.local", 19999)
        state.onAddrInfoFound("PeerA", "10.0.0.5", isAdd = true)
        assertEquals(19999, sink.devices.single().port)

        state.onResolved("PeerA", "peera.local", 20001)
        assertEquals(20001, sink.devices.single().port)
        assertEquals(1, sink.devices.size)
    }

    @Test
    fun `browse remove cleans up device, pending state, and active subordinates`() {
        val sink = RecordingSink()
        val state = BonjourState("self", sink)

        state.onBrowseAdd("PeerA", 0)
        state.onResolved("PeerA", "peera.local", 19999)
        state.onAddrInfoFound("PeerA", "10.0.0.5", isAdd = true)
        assertEquals(1, sink.devices.size)

        state.onBrowseRemove("PeerA")
        assertTrue(sink.devices.isEmpty(), "device removed")
        assertTrue(sink.opened.contains("closeResolve" to "PeerA"))
        assertTrue(sink.opened.contains("closeAddrInfo" to "PeerA"))

        // After remove, a new add-resolve-addrinfo cycle works again.
        state.onBrowseAdd("PeerA", 0)
        state.onResolved("PeerA", "peera.local", 22222)
        state.onAddrInfoFound("PeerA", "10.0.0.6", isAdd = true)
        assertEquals(1, sink.devices.size)
        assertEquals(22222, sink.devices.single().port)
    }

    @Test
    fun `addrInfo with isAdd=false drops pending IP without removing device`() {
        val sink = RecordingSink()
        val state = BonjourState("self", sink)

        state.onBrowseAdd("PeerA", 0)
        state.onResolved("PeerA", "peera.local", 19999)
        state.onAddrInfoFound("PeerA", "10.0.0.5", isAdd = true)
        val baseline = sink.publishCount

        state.onAddrInfoFound("PeerA", "10.0.0.5", isAdd = false)
        // Device entry stays — Browse-remove is the canonical "peer gone" signal.
        assertEquals(1, sink.devices.size)
        assertEquals(baseline, sink.publishCount, "no spurious publish on IP transition")
    }

    @Test
    fun `self filter ignores configured device name`() {
        val sink = RecordingSink()
        val state = BonjourState("Self", sink)

        state.onBrowseAdd("Self", 0)
        state.onResolved("Self", "self.local", 18000)
        state.onAddrInfoFound("Self", "10.0.0.1", isAdd = true)
        assertEquals(0, sink.publishCount, "own service must never appear in devices list")
        assertTrue(sink.opened.none { it.first == "openResolve" }, "no resolve for self")
    }

    @Test
    fun `late Resolved arriving after BrowseRemove is dropped`() {
        // Reproduces the small race the reviewer flagged: with Channel.UNLIMITED, a
        // Resolved callback can be queued before BrowseRemove and then consumed after
        // it. Without the membership gate, this resurrects the peer.
        val sink = RecordingSink()
        val state = BonjourState("self", sink)

        state.onBrowseAdd("PeerA", 0)
        state.onResolved("PeerA", "peera.local", 19999)
        state.onAddrInfoFound("PeerA", "10.0.0.5", isAdd = true)
        state.onBrowseRemove("PeerA")
        assertTrue(sink.devices.isEmpty(), "device removed by BrowseRemove")

        // Late event — should be dropped, not re-add the peer or re-open subordinates.
        val openedBefore = sink.opened.size
        state.onResolved("PeerA", "peera.local", 20000)
        assertTrue(sink.devices.isEmpty(), "stale Resolved must not resurrect peer")
        assertEquals(openedBefore, sink.opened.size, "stale Resolved must not open new subordinates")
    }

    @Test
    fun `late AddrInfoFound arriving after BrowseRemove is dropped`() {
        val sink = RecordingSink()
        val state = BonjourState("self", sink)

        state.onBrowseAdd("PeerA", 0)
        state.onResolved("PeerA", "peera.local", 19999)
        state.onAddrInfoFound("PeerA", "10.0.0.5", isAdd = true)
        state.onBrowseRemove("PeerA")
        val publishesBefore = sink.publishCount

        state.onAddrInfoFound("PeerA", "10.0.0.6", isAdd = true)
        assertTrue(sink.devices.isEmpty(), "stale AddrInfoFound must not resurrect peer")
        assertEquals(publishesBefore, sink.publishCount, "no spurious publish from stale event")
    }

    @Test
    fun `canonical name from RegisterReply removes self entry that slipped in`() {
        // mDNSResponder may rename us on conflict ("Self" → "Self (2)"). Until the
        // canonical name is known, the configured name is used as a self-filter, so
        // a device under the renamed name can briefly appear in the list.
        val sink = RecordingSink()
        val state = BonjourState("Self", sink)

        state.onBrowseAdd("Self (2)", 0)
        state.onResolved("Self (2)", "self.local", 18000)
        state.onAddrInfoFound("Self (2)", "10.0.0.1", isAdd = true)
        assertEquals(1, sink.devices.size, "before canonical name we publish the renamed entry")

        state.ownNameAssigned("Self (2)")
        assertTrue(sink.devices.isEmpty(), "self entry removed once canonical name known")
    }
}
