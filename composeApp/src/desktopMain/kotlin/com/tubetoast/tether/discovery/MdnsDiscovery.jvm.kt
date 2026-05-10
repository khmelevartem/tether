package com.tubetoast.tether.discovery

import com.tubetoast.tether.discovery.bonjour.MdnsDiscoveryBonjour
import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.flow.StateFlow

internal interface MdnsDiscoveryDelegate {
    val discoveredDevices: StateFlow<List<Device>>

    fun start(deviceName: String, port: Int)

    fun stop()
}

/**
 * macOS → [MdnsDiscoveryBonjour] (DNS-SD IPC; JmDNS can't see external WiFi peers on macOS).
 * Linux/Windows → [MdnsDiscoveryJmdns].
 *
 * [stop] may block up to ~200 ms on macOS; invoke from a background dispatcher in UI code.
 */
actual class MdnsDiscovery {
    private val delegate: MdnsDiscoveryDelegate =
        if (isMacOsHost()) MdnsDiscoveryBonjour() else MdnsDiscoveryJmdns()

    actual val discoveredDevices: StateFlow<List<Device>> get() = delegate.discoveredDevices

    actual fun start(deviceName: String, port: Int) = delegate.start(deviceName, port)

    actual fun stop() = delegate.stop()
}

private fun isMacOsHost(): Boolean =
    System
        .getProperty("os.name")
        .orEmpty()
        .lowercase()
        .startsWith("mac")
