package com.tubetoast.tether.discovery

import com.tubetoast.tether.discovery.bonjour.MdnsDiscoveryBonjour
import com.tubetoast.tether.identity.DeviceIdentityStore
import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

/**
 * macOS → [MdnsDiscoveryBonjour] (DNS-SD IPC; JmDNS can't see external WiFi peers on macOS).
 * Linux/Windows → [MdnsDiscoveryJmdns].
 */
actual class MdnsDiscovery(
    store: DiscoveredDevicesStore,
    deviceIdentityStore: DeviceIdentityStore,
) : DeviceDiscovery, CanonicalNameSource {
    private val delegate: OwnNameDiscovery =
        if (isMacOsHost()) {
            MdnsDiscoveryBonjour(store, deviceIdentityStore)
        } else {
            MdnsDiscoveryJmdns(store, Dispatchers.IO, deviceIdentityStore)
        }

    actual override val discoveredDevices: StateFlow<List<Device>> get() = delegate.discoveredDevices

    actual override val ownPublishedName: StateFlow<String?> get() = delegate.ownPublishedName

    actual override suspend fun start(deviceName: String, port: Int) = delegate.start(deviceName, port)

    actual override suspend fun stop() = delegate.stop()

    actual override suspend fun republish(name: String) = delegate.republish(name)
}

internal interface OwnNameDiscovery : DeviceDiscovery {
    val ownPublishedName: StateFlow<String?>
}

private fun isMacOsHost(): Boolean =
    System
        .getProperty("os.name")
        .orEmpty()
        .lowercase()
        .startsWith("mac")
