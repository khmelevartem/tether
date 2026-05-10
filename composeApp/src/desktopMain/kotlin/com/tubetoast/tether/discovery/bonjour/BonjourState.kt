package com.tubetoast.tether.discovery.bonjour

import com.tubetoast.tether.protocol.Device

/**
 * State machine over the Browse → Resolve → GetAddrInfo callback chain. Pure
 * Kotlin, no JNA — effects (open/close subordinate refs, publish devices) go
 * through [Sink] so [MdnsDiscoveryBonjour] wires the JNA side and tests wire
 * fakes.
 *
 * **Self-filter.** [ownName] starts as the configured `deviceName`. mDNSResponder
 * may rename our service on conflict (e.g. `Foo` → `Foo (2)`); the canonical
 * name arrives asynchronously via [ownNameAssigned] from the DNSServiceRegister
 * callback. Until that happens the configured name is used as a starting filter,
 * and any self entry that slipped in under the renamed name is removed when the
 * canonical name is set.
 *
 * **Stale-event gate.** [onResolved] and [onAddrInfoFound] early-return when the
 * peer name is no longer in [activeResolves] / [activeAddrInfos]. Without this,
 * an event queued by the per-name poll loop before BrowseRemove was processed
 * would resurrect a peer entry after [onBrowseRemove] cleaned it up.
 */
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

    /**
     * Apply the canonical name reported by the `DNSServiceRegister` callback.
     * If the configured name has been replaced (e.g. on conflict) and a self
     * entry was already published under the new name, drop it.
     */
    fun ownNameAssigned(canonicalName: String) {
        if (canonicalName == ownName) return
        ownName = canonicalName
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
        if (name !in activeResolves) return
        pendingPorts[name] = port
        emitIfReady(name)
        if (activeAddrInfos.add(name)) {
            sink.openAddrInfo(name, hostname)
        }
    }

    /**
     * @param isAdd `true` for a new address (`kDNSServiceFlagsAdd`); `false`
     *   means the address is going away. Per dns_sd.h, `false` should drop the
     *   IP from any cached address list. We deliberately keep the device entry
     *   in place — BrowseRemove is the canonical "peer gone" signal, and
     *   removing the device on every routine IP rotation would flicker the UI.
     */
    fun onAddrInfoFound(name: String, ipv4: String, isAdd: Boolean) {
        if (name == ownName) return
        if (name !in activeAddrInfos) return
        if (isAdd) {
            pendingIps[name] = ipv4
            emitIfReady(name)
        } else {
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

    /** Effects emitted by the state machine. */
    internal interface Sink {
        fun openResolve(name: String, interfaceIndex: Int)

        fun closeResolve(name: String)

        fun openAddrInfo(name: String, hostname: String)

        fun closeAddrInfo(name: String)

        fun publishDevices(devices: List<Device>)
    }
}
