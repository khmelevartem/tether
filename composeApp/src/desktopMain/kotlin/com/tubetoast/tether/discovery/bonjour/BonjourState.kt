package com.tubetoast.tether.discovery.bonjour

import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.protocol.Device

/**
 * State machine over the Browse → Resolve → GetAddrInfo callback chain. Pure
 * Kotlin, no JNA — effects go through [Sink] and [DiscoveredDevicesStore].
 *
 * Self-filter: a resolved device is skipped if [isSelf] returns `true` for its
 * (host, port) pair. This is rename-proof — mDNSResponder may assign a canonical
 * name different from the requested name, but the self-filter never depends on the name.
 *
 * [onResolved] and [onAddrInfoFound] gate on [activeResolves] / [activeAddrInfos]
 * to drop events queued before a BrowseRemove was processed.
 */
internal class BonjourState(
    private val store: DiscoveredDevicesStore,
    private val sink: Sink,
    /** Returns `true` when the given (host, port) pair identifies this device. */
    private val isSelf: (host: String, port: Int) -> Boolean,
) {
    /** Platform translation: native callbacks identify services by name; we track name → Device for id-based store ops. */
    private val nameToDevice = mutableMapOf<String, Device>()

    private val activeResolves = mutableSetOf<String>()
    private val activeAddrInfos = mutableSetOf<String>()
    private val pendingPorts = mutableMapOf<String, Int>()
    private val pendingIps = mutableMapOf<String, String>()

    fun onBrowseAdd(name: String, interfaceIndex: Int) {
        if (!activeResolves.add(name)) return
        sink.openResolve(name, interfaceIndex)
    }

    fun onBrowseRemove(name: String) {
        val hadResolve = activeResolves.remove(name)
        val hadAddr = activeAddrInfos.remove(name)
        if (hadResolve) sink.closeResolve(name)
        if (hadAddr) sink.closeAddrInfo(name)
        cleanupName(name)
    }

    fun onResolved(name: String, hostname: String, port: Int) {
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
        if (name !in activeAddrInfos) return
        if (isAdd) {
            pendingIps[name] = ipv4
            emitIfReady(name)
        } else {
            pendingIps.remove(name)
        }
    }

    private fun cleanupName(name: String) {
        pendingPorts.remove(name)
        pendingIps.remove(name)
        val device = nameToDevice.remove(name) ?: return
        store.removeById(device.id)
    }

    private fun emitIfReady(name: String) {
        val ip = pendingIps[name] ?: return
        val port = pendingPorts[name] ?: return
        if (isSelf(ip, port)) return
        val device = Device(id = "$name@$ip:$port", name = name, host = ip, port = port)
        val previous = nameToDevice.put(name, device)
        if (previous != null && previous.id != device.id) store.removeById(previous.id)
        store.upsert(device)
    }

    internal interface Sink {
        fun openResolve(name: String, interfaceIndex: Int)

        fun closeResolve(name: String)

        fun openAddrInfo(name: String, hostname: String)

        fun closeAddrInfo(name: String)
    }
}
