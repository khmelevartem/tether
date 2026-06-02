package com.tubetoast.tether.discovery.bonjour

import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.protocol.Device

/** State machine over the Browse → Resolve → GetAddrInfo callback chain. */
internal class BonjourState(
    private val store: DiscoveredDevicesStore,
    private val sink: Sink,
    private val ownFingerprint: String,
    private val isSelf: (host: String, port: Int) -> Boolean,
) {
    private val activeResolves = mutableSetOf<String>()
    private val activeAddrInfos = mutableSetOf<String>()
    private val pendingPorts = mutableMapOf<String, Int>()
    private val pendingIps = mutableMapOf<String, String>()
    private val pendingFingerprints = mutableMapOf<String, String>()

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

    fun onResolved(name: String, hostname: String, port: Int, peerFingerprint: String?) {
        if (name !in activeResolves) return
        pendingPorts[name] = port
        if (peerFingerprint != null) pendingFingerprints[name] = peerFingerprint
        emitIfReady(name)
        if (activeAddrInfos.add(name)) {
            sink.openAddrInfo(name, hostname)
        }
    }

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
        pendingFingerprints.remove(name)
        // Look up the store, not the pending map — a multi-rename storm produces multiple
        // BrowseAdd events for one fingerprint, but the store retains only the latest name
        // (Rule 1). A removal under a stale intermediate name must not evict the live entry.
        store.devices.value
            .firstOrNull { it.name == name }
            ?.fingerprint
            ?.let(store::removeByFingerprint)
    }

    private fun emitIfReady(name: String) {
        val ip = pendingIps[name] ?: return
        val port = pendingPorts[name] ?: return
        // Defer the upsert until TXT resolves — the store never holds anonymous entries.
        val peerFingerprint = pendingFingerprints[name] ?: return
        if (peerFingerprint == ownFingerprint) return
        if (isSelf(ip, port)) return
        store.upsert(Device(name = name, host = ip, port = port, fingerprint = peerFingerprint))
    }

    internal interface Sink {
        fun openResolve(name: String, interfaceIndex: Int)

        fun closeResolve(name: String)

        fun openAddrInfo(name: String, hostname: String)

        fun closeAddrInfo(name: String)
    }
}
