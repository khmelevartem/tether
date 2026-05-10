package com.tubetoast.tether.discovery.bonjour

import com.tubetoast.tether.protocol.Device

// State-machine over Browse → Resolve → GetAddrInfo events. Pure logic, no JNA.
// Effects (open/close subordinate refs, publish devices) are routed through the
// Sink so MdnsDiscoveryBonjour can wire the JNA side and tests can wire fakes.
//
// Self-filter: ownName is set asynchronously from the DNSServiceRegister callback,
// because mDNSResponder may rename our service on conflict (e.g. "Foo" → "Foo (2)").
// Until ownNameAssigned() is called, we treat ourselves as not-yet-named and accept
// the configured deviceName as a starting filter; the canonical name overrides it
// when register completes.
internal class BonjourState(
    deviceName: String,
    private val sink: Sink,
) {
    private var ownName: String = deviceName

    private val activeResolves = mutableSetOf<String>()
    private val activeAddrInfos = mutableSetOf<String>()
    private val pendingPorts = mutableMapOf<String, Int>()
    private val pendingIps = mutableMapOf<String, String>()
    private var devices: List<Device> = emptyList()

    fun ownNameAssigned(canonicalName: String) {
        if (canonicalName == ownName) return
        ownName = canonicalName
        // If we previously published ourselves as a peer (because the canonical name
        // wasn't yet known when the browse callback fired), drop that entry now.
        if (cleanupName(canonicalName)) {
            sink.publishDevices(devices)
        }
    }

    fun onBrowseAdd(name: String, interfaceIndex: Int) {
        if (name == ownName) return
        if (!activeResolves.add(name)) return
        sink.openResolve(name, interfaceIndex)
    }

    fun onBrowseRemove(name: String) {
        if (name == ownName) return
        val hadResolve = activeResolves.remove(name)
        val hadAddr = activeAddrInfos.remove(name)
        if (hadResolve) sink.closeResolve(name)
        if (hadAddr) sink.closeAddrInfo(name)
        if (cleanupName(name)) sink.publishDevices(devices)
    }

    fun onResolved(name: String, hostname: String, port: Int) {
        if (name == ownName) return
        // Membership gate: a Resolved event may be queued before BrowseRemove was
        // processed, so by the time we consume it the peer might already be gone.
        // Without this, we'd resurrect the device entry and re-open an addrInfo for
        // a removed peer.
        if (name !in activeResolves) return
        pendingPorts[name] = port
        emitIfReady(name)
        if (activeAddrInfos.add(name)) {
            sink.openAddrInfo(name, hostname)
        }
    }

    fun onAddrInfoFound(name: String, ipv4: String, isAdd: Boolean) {
        if (name == ownName) return
        // Same membership gate as onResolved — drop stale addrInfo callbacks
        // queued before BrowseRemove cleaned up the peer.
        if (name !in activeAddrInfos) return
        if (isAdd) {
            pendingIps[name] = ipv4
            emitIfReady(name)
        } else {
            // dns_sd reports flag=0 to mean "this address is going away" — drop it from
            // pending IPs but keep the device entry until either the next IP-add resolves
            // or BrowseRemove fires. Removing the device here would flicker on every
            // routine IP rotation.
            pendingIps.remove(name)
        }
    }

    private fun cleanupName(name: String): Boolean {
        pendingPorts.remove(name)
        pendingIps.remove(name)
        val updated = devices.filterNot { it.name == name }
        if (updated.size != devices.size) {
            devices = updated
            return true
        }
        return false
    }

    private fun emitIfReady(name: String) {
        val ip = pendingIps[name] ?: return
        val port = pendingPorts[name] ?: return
        val device = Device(id = "$name@$ip:$port", name = name, host = ip, port = port)
        val updated = devices.filterNot { it.name == name } + device
        if (updated != devices) {
            devices = updated
            sink.publishDevices(devices)
        }
    }

    internal interface Sink {
        fun openResolve(name: String, interfaceIndex: Int)

        fun closeResolve(name: String)

        fun openAddrInfo(name: String, hostname: String)

        fun closeAddrInfo(name: String)

        fun publishDevices(devices: List<Device>)
    }
}
