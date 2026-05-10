package com.tubetoast.tether.discovery

import com.tubetoast.tether.discovery.bonjour.MdnsDiscoveryBonjour
import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.flow.StateFlow

/**
 * macOS → [MdnsDiscoveryBonjour] (DNS-SD IPC; JmDNS can't see external WiFi peers on macOS).
 * Linux/Windows → [MdnsDiscoveryJmdns].
 *
 * [stop] may block up to ~200 ms on macOS; invoke from a background dispatcher in UI code.
 */
actual class MdnsDiscovery {
    private val jmdns: MdnsDiscoveryJmdns? = if (isMacOsHost()) null else MdnsDiscoveryJmdns()
    private val bonjour: MdnsDiscoveryBonjour? = if (isMacOsHost()) MdnsDiscoveryBonjour() else null

    actual val discoveredDevices: StateFlow<List<Device>> =
        bonjour?.discoveredDevices ?: jmdns!!.discoveredDevices

    actual fun start(deviceName: String, port: Int) {
        bonjour?.start(deviceName, port) ?: jmdns!!.start(deviceName, port)
    }

    actual fun stop() {
        bonjour?.stop() ?: jmdns!!.stop()
    }
}

private fun isMacOsHost(): Boolean =
    System
        .getProperty("os.name")
        .orEmpty()
        .lowercase()
        .startsWith("mac")
