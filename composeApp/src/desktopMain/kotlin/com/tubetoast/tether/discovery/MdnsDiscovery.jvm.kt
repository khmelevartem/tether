package com.tubetoast.tether.discovery

import com.tubetoast.tether.discovery.bonjour.MdnsDiscoveryBonjour
import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.flow.StateFlow

/**
 * JVM Desktop discovery, dispatched by host OS:
 *
 * - **macOS:** Bonjour (DNS-SD via mDNSResponder IPC) — see [MdnsDiscoveryBonjour].
 *   The macOS kernel routes incoming WiFi mDNS multicast exclusively to
 *   mDNSResponder; user-space sockets bound to `224.0.0.251:5353` do not see
 *   external peers, so JmDNS cannot complete discovery beyond Mac↔Mac loopback.
 *   See issue #47 and `docs/knowledge/macos-mdns-bonjour.md`.
 * - **Linux/Windows:** [MdnsDiscoveryJmdns] — no system mDNS daemon competes for
 *   the port and JmDNS receives announcements directly from peers.
 *
 * [stop] can block for up to ~200 ms on the macOS path while polling coroutines
 * observe their cancellation flag (see [MdnsDiscoveryBonjour]). UI callers
 * should invoke from a background dispatcher.
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
