package com.tubetoast.tether.discovery.bonjour

import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.protocol.Device

/** State machine over the Browse → Resolve → GetAddrInfo callback chain. */
internal class BonjourState(
    private val store: DiscoveredDevicesStore,
    private val sink: Sink,
    private val isSelf: (host: String, port: Int) -> Boolean,
) {
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
        store.removeByName(name)
    }

    private fun emitIfReady(name: String) {
        val ip = pendingIps[name] ?: return
        val port = pendingPorts[name] ?: return
        if (isSelf(ip, port)) return
        val device = Device(id = "$name@$ip:$port", name = name, host = ip, port = port)
        store.upsert(device)
    }

    internal interface Sink {
        fun openResolve(name: String, interfaceIndex: Int)

        fun closeResolve(name: String)

        fun openAddrInfo(name: String, hostname: String)

        fun closeAddrInfo(name: String)
    }
}
