package com.tubetoast.tether.discovery.bonjour

import com.tubetoast.tether.protocol.Device

/**
 * State machine over the Browse → Resolve → GetAddrInfo callback chain. Pure
 * Kotlin, no JNA — effects go through [Sink].
 *
 * [ownName] starts as the configured `deviceName`; mDNSResponder may rename on
 * conflict (e.g. `Foo` → `Foo (2)`) and delivers the canonical name via
 * [ownNameAssigned]. [onResolved] and [onAddrInfoFound] gate on
 * [activeResolves] / [activeAddrInfos] to drop events queued before a
 * BrowseRemove was processed.
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

    internal interface Sink {
        fun openResolve(name: String, interfaceIndex: Int)

        fun closeResolve(name: String)

        fun openAddrInfo(name: String, hostname: String)

        fun closeAddrInfo(name: String)

        fun publishDevices(devices: List<Device>)
    }
}
